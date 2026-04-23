package com.example.ioutils;

import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;

import static org.mockito.Mockito.*;

/**
 * IoUtils单元测试
 */
public class IoUtilsTest {

    @Test
    public void testCloseQuietlyWithNull() {
        // 测试null参数不抛异常
        IoUtils.closeQuietly((Closeable) null);
        // 如果到这里没抛异常，测试通过
    }

    @Test
    public void testCloseQuietlyWithValidResource() throws IOException {
        // 测试正常关闭
        Closeable mockCloseable = mock(Closeable.class);
        IoUtils.closeQuietly(mockCloseable);
        verify(mockCloseable).close();
    }

    @Test
    public void testCloseQuietlyWithException() throws IOException {
        // 测试关闭时抛出异常，不传播
        Closeable mockCloseable = mock(Closeable.class);
        doThrow(new IOException("关闭失败")).when(mockCloseable).close();

        // 不应该抛异常
        IoUtils.closeQuietly(mockCloseable);

        verify(mockCloseable).close();
    }

    @Test
    public void testCloseQuietlyMultiple() throws IOException {
        // 测试批量关闭
        Closeable mock1 = mock(Closeable.class);
        Closeable mock2 = mock(Closeable.class);
        Closeable mock3 = mock(Closeable.class);

        IoUtils.closeQuietly(mock1, mock2, mock3);

        verify(mock1).close();
        verify(mock2).close();
        verify(mock3).close();
    }

    @Test
    public void testCloseQuietlyMultipleWithException() throws IOException {
        // 测试批量关闭时部分失败，其他仍应关闭
        Closeable mock1 = mock(Closeable.class);
        Closeable mock2 = mock(Closeable.class);
        Closeable mock3 = mock(Closeable.class);

        doThrow(new IOException("关闭失败")).when(mock2).close();

        IoUtils.closeQuietly(mock1, mock2, mock3);

        verify(mock1).close();
        verify(mock2).close();
        verify(mock3).close();  // 即使mock2失败，mock3也应被关闭
    }

    @Test
    public void testCloseAndThrowWithNull() throws IOException {
        // 测试null参数不抛异常
        IoUtils.closeAndThrow(null);
    }

    @Test(expected = IOException.class)
    public void testCloseAndThrowWithException() throws IOException {
        // 测试关闭时抛出异常，应传播
        Closeable mockCloseable = mock(Closeable.class);
        doThrow(new IOException("关闭失败")).when(mockCloseable).close();

        IoUtils.closeAndThrow(mockCloseable);
    }

    @Test(expected = RuntimeException.class)
    public void testCloseAndThrowUncheckedWithException() throws IOException {
        // 测试关闭时抛出异常，应包装为RuntimeException
        Closeable mockCloseable = mock(Closeable.class);
        doThrow(new IOException("关闭失败")).when(mockCloseable).close();

        IoUtils.closeAndThrowUnchecked(mockCloseable);
    }

    @Test
    public void testCloseAndThrowUncheckedWithNull() {
        // 测试null参数不抛异常
        IoUtils.closeAndThrowUnchecked(null);
    }

    @Test
    public void testRealResource() {
        // 测试真实资源关闭
        StringReader reader = new StringReader("test");
        IoUtils.closeQuietly(reader);
        // 如果到这里没抛异常，测试通过
    }
}
