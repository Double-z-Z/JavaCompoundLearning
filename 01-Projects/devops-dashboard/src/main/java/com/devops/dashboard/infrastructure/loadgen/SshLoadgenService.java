package com.devops.dashboard.infrastructure.loadgen;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.host.dto.HostTopology;
import com.devops.dashboard.application.loadgen.CommandExecutionResult;
import com.devops.dashboard.application.loadgen.LoadgenService;
import com.devops.dashboard.domain.exception.host.HostNotFoundException;
import com.devops.dashboard.domain.host.*;
import com.devops.dashboard.domain.loadgen.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSH 远程压测服务实现。
 *
 * <p>通过 SSH 在指定的压测机（Loadgen Host）上执行 wrk/hey/ab 等压测工具，
 * 解析输出并返回结构化的 {@link LoadTestResult}。</p>
 *
 * @see SshCommandExecutor
 * @see LoadgenService
 */
@Service
public class SshLoadgenService implements LoadgenService {

    private static final Logger log = LoggerFactory.getLogger(SshLoadgenService.class);

    private final HostService hostService;
    private final SshCommandExecutor sshExecutor;
    private final HostRepository hostRepository;

    public SshLoadgenService(HostService hostService, SshCommandExecutor sshExecutor, HostRepository hostRepository) {
        this.hostService = hostService;
        this.sshExecutor = sshExecutor;
        this.hostRepository = hostRepository;
    }

    @Override
    public Mono<LoadTestResult> executeLoadTest(LoadTestSpec spec) {
        return Mono.fromCallable(() -> {
            log.info("Executing load test: tool={}, target={}, connections={}, duration={}s",
                    spec.getTool(), spec.getTargetUrl(), spec.getConnections(), spec.getDuration().getSeconds());

            Host loadgenHost = resolveAndValidateLoadgenHost(spec);
            String command = buildCommand(spec);

            log.debug("Load test command: {}", command);

            LocalDateTime startTime = LocalDateTime.now();
            int timeoutSec = (int) spec.getDuration().getSeconds() + 30;

            SshCommandExecutor.CommandResult result = sshExecutor.execute(
                    loadgenHost.getAccess(), command, timeoutSec);

            LocalDateTime endTime = LocalDateTime.now();
            Duration actualDuration = Duration.between(startTime, endTime);

            if (!result.isSuccess()) {
                log.warn("Load test command failed with exit code {}: {}", result.getExitCode(), result.getStderr());
                return parseFailedResult(result, spec.getTool());
            }

            return parseOutput(result.getStdout(), spec.getTool())
                    .status(LoadTestStatus.COMPLETED)
                    .startTime(startTime)
                    .endTime(endTime)
                    .actualDuration(actualDuration)
                    .tool(spec.getTool())
                    .rawOutput(truncateOutput(result.getStdout()))
                    .build();
        });
    }

    @Override
    public Mono<HealthCheckResult> executeHealthCheck(HealthCheckSpec spec) {
        return Mono.fromCallable(() -> {
            log.info("Executing health check: url={}, timeout={}s",
                    spec.getTargetUrl(), spec.getTimeout().getSeconds());

            String curlCmd = String.format("curl -s -o /dev/null -w '%%{http_code} %%{time_total}' " +
                    "--max-time %d '%s'", spec.getTimeout().getSeconds(), spec.getTargetUrl());

            SshCommandExecutor.CommandResult result = sshExecutor.executeLocal(curlCmd,
                    (int) spec.getTimeout().getSeconds() + 5);

            if (!result.isSuccess()) {
                return HealthCheckResult.unhealthy(spec.getTargetUrl(),
                        "curl failed: " + result.getStderr());
            }

            String output = result.getStdout().trim();
            String[] parts = output.split("\\s+");

            if (parts.length < 2) {
                return HealthCheckResult.unhealthy(spec.getTargetUrl(),
                        "Unexpected output: " + output);
            }

            try {
                int statusCode = Integer.parseInt(parts[0]);
                double responseTimeSec = Double.parseDouble(parts[1]);
                boolean healthy = statusCode == spec.getExpectedStatusCode();

                return HealthCheckResult.builder()
                        .healthy(healthy)
                        .targetUrl(spec.getTargetUrl())
                        .statusCode(statusCode)
                        .responseTime(Duration.ofMillis((long) (responseTimeSec * 1000)))
                        .errorMessage(healthy ? null : "Expected " + spec.getExpectedStatusCode()
                                + ", got " + statusCode)
                        .build();
            } catch (NumberFormatException e) {
                return HealthCheckResult.unhealthy(spec.getTargetUrl(),
                        "Failed to parse output: " + output);
            }
        });
    }

    @Override
    public Mono<CommandExecutionResult> executeCommand(String hostId, String command, int timeoutSeconds) {
        return Mono.fromCallable(() -> {
            log.info("Executing remote command on {}: {}", hostId, command);

            Host domainHost = findDomainHostOrThrow(HostId.of(hostId));

            SshCommandExecutor.CommandResult result = sshExecutor.execute(
                    domainHost.getAccess(), command, Math.max(timeoutSeconds, 10));

            return new CommandExecutionResult(
                    result.getExitCode(), result.getStdout(), result.getStderr(), result.getDurationMs());
        });
    }

    @Override
    public List<String> getAvailableTools(String loadgenHostId) {
        HostTopology topology = hostService.getTopology();
        return topology.getHosts().stream()
                .filter(h -> h.id().equals(loadgenHostId))
                .findFirst()
                .map(HostTopology.HostDto::loadgenTools)
                .orElse(List.of());
    }

    private Host resolveAndValidateLoadgenHost(LoadTestSpec spec) {
        if (spec.getLoadgenHostId() == null || spec.getLoadgenHostId().isBlank()) {
            throw new IllegalArgumentException("loadgenHostId is required for remote execution");
        }

        HostId hostId = HostId.of(spec.getLoadgenHostId());

        hostService.validateRole(hostId, HostRole.LOADGEN);

        Host host = findDomainHostOrThrow(hostId);

        if (!host.hasLoadgenTool(spec.getTool())) {
            throw new IllegalArgumentException(
                    "Host " + hostId.value() + " does not have tool " + spec.getTool().getCommand());
        }

        return host;
    }

    private Host findDomainHostOrThrow(HostId hostId) {
        var topology = hostService.getTopology();
        boolean exists = topology.getHosts().stream()
                .anyMatch(h -> h.id().equals(hostId.value()));
        
        if (!exists) {
            throw new HostNotFoundException(hostId.value());
        }
        
        return hostRepository.findById(hostId)
                .orElseThrow(() -> new HostNotFoundException(hostId.value()));
    }

    private String buildCommand(LoadTestSpec spec) {
        return switch (spec.getTool()) {
            case WRK -> buildWrkCommand(spec);
            case HEY -> buildHeyCommand(spec);
            case AB -> buildAbCommand(spec);
        };
    }

    private String buildWrkCommand(LoadTestSpec spec) {
        int threads = Math.min(spec.getThreads(), spec.getConnections());
        return String.format("wrk -t%d -c%d -d%ds --latency '%s'",
                threads, spec.getConnections(), spec.getDuration().getSeconds(), spec.getTargetUrl());
    }

    private String buildHeyCommand(LoadTestSpec spec) {
        int totalRequests = spec.getConnections() * (int) (spec.getDuration().getSeconds() * 10);
        return String.format("hey -n %d -c %d -z '%ds' '%s'",
                totalRequests, spec.getConnections(), spec.getDuration().getSeconds(), spec.getTargetUrl());
    }

    private String buildAbCommand(LoadTestSpec spec) {
        int totalRequests = spec.getConnections() * (int) (spec.getDuration().getSeconds() * 10);
        return String.format("ab -n %d -c %d '%s'",
                totalRequests, spec.getConnections(), spec.getTargetUrl());
    }

    private LoadTestResult.Builder parseOutput(String rawOutput, LoadgenTool tool) {
        return switch (tool) {
            case WRK -> parseWrkOutput(rawOutput);
            case HEY -> parseHeyOutput(rawOutput);
            case AB -> parseAbOutput(rawOutput);
        };
    }

    private LoadTestResult.Builder parseWrkOutput(String output) {
        LoadTestResult.Builder builder = LoadTestResult.builder();

        matchFirst(output, "Requests/sec:\\s*([\\d.]+)", m ->
                builder.requestsPerSecond(new BigDecimal(m.group(1)).setScale(2, RoundingMode.HALF_UP)));

        matchFirst(output, "Latency\\s*([\\d.]+)ms", m ->
                builder.latencyAvg(Double.parseDouble(m.group(1))));

        matchFirst(output, "50%\\s*([\\d.]+)", m ->
                builder.latencyP50(Double.parseDouble(m.group(1))));

        matchFirst(output, "90%\\s*([\\d.]+)", m ->
                builder.latencyP90(Double.parseDouble(m.group(1))));

        matchFirst(output, "99%\\s*([\\d.]+)", m ->
                builder.latencyP99(Double.parseDouble(m.group(1))));

        long errors = 0;
        Matcher errMatcher = Pattern.compile("Socket errors:\\s*connect ([\\d]+),").matcher(output);
        if (errMatcher.find()) errors += Long.parseLong(errMatcher.group(1));
        builder.totalErrors(errors);

        Matcher reqMatcher = Pattern.compile("(\\d+) requests in ([\\d.]+)").matcher(output);
        if (reqMatcher.find()) {
            builder.totalRequests(Long.parseLong(reqMatcher.group(1)));
        }

        LoadTestResult partial = builder.build();
        double errorRate = partial.getTotalRequests() > 0
                ? (double) errors / partial.getTotalRequests() * 100 : 0;
        builder.errorRate(errorRate);

        return builder;
    }

    private LoadTestResult.Builder parseHeyOutput(String output) {
        LoadTestResult.Builder builder = LoadTestResult.builder();

        matchFirst(output, "[^\\d]([\\d.]+) req/s", m ->
                builder.requestsPerSecond(new BigDecimal(m.group(1)).setScale(2, RoundingMode.HALF_UP)));

        matchFirst(output, "average:\\s*([\\d.]+)", m ->
                builder.latencyAvg(Double.parseDouble(m.group(1))));

        matchFirst(output, "fastest:\\s*([\\d.]+)", m ->
                builder.latencyP50(Double.parseDouble(m.group(1))));

        matchFirst(output, "slowest:\\s*([\\d.]+)", m ->
                builder.latencyP99(Double.parseDouble(m.group(1))));

        matchFirst(output, "responses:\\s*(\\d+)", m ->
                builder.totalRequests(Long.parseLong(m.group(1))));

        return builder.errorRate(0);
    }

    private LoadTestResult.Builder parseAbOutput(String output) {
        LoadTestResult.Builder builder = LoadTestResult.builder();

        matchFirst(output, "Requests per second:\\s*([\\d.]+) [\\[#]*/sec", m ->
                builder.requestsPerSecond(new BigDecimal(m.group(1)).setScale(2, RoundingMode.HALF_UP)));

        matchFirst(output, "Time per request:\\s*([\\d.]+) \\[ms\\]", m ->
                builder.latencyAvg(Double.parseDouble(m.group(1))));

        matchFirst(output, "Complete requests:\\s*(\\d+)", m ->
                builder.totalRequests(Long.parseLong(m.group(1))));

        matchFirst(output, "Failed requests:\\s*(\\d+)", m ->
                builder.totalErrors(Long.parseLong(m.group(1))));

        LoadTestResult partial = builder.build();
        double errRate = partial.getTotalRequests() > 0
                ? (double) partial.getTotalErrors() / partial.getTotalRequests() * 100 : 0;
        builder.errorRate(errRate);

        return builder;
    }

    private void matchFirst(String input, String regex, java.util.function.Consumer<Matcher> action) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        if (matcher.find()) {
            action.accept(matcher);
        }
    }

    private LoadTestResult parseFailedResult(SshCommandExecutor.CommandResult result, LoadgenTool tool) {
        return LoadTestResult.builder()
                .status(LoadTestStatus.FAILED)
                .tool(tool)
                .rawOutput("exitCode=" + result.getExitCode() + "\nstderr=" + result.getStderr())
                .totalErrors(1)
                .errorRate(100.0)
                .build();
    }

    private String truncateOutput(String output) {
        if (output == null) return "";
        return output.length() > 4096 ? output.substring(0, 4096) + "\n... [truncated]" : output;
    }
}
