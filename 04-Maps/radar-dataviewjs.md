```dataviewjs
// 能力雷达图 - DataviewJS 动态生成
// 修改下方数据即可自动重绘

const data = {
    dimensions: [
        { name: "核心技术", score: 65, level: "L2.5" },
        { name: "问题解决", score: 55, level: "L2" },
        { name: "架构设计", score: 45, level: "L1.5" },
        { name: "工程素养", score: 35, level: "L1" },
        { name: "持续学习", score: 60, level: "L2" }
    ],
    target: 75,
    title: "能力雷达图 - 2026.04"
};

const width = 520;
const height = 420;
const cx = width / 2;
const cy = height / 2 + 10;
const maxR = 140;
const angles = [270, 342, 54, 126, 198].map(a => a * Math.PI / 180);

function pt(r, idx) {
    return {
        x: cx + r * Math.cos(angles[idx]),
        y: cy + r * Math.sin(angles[idx])
    };
}

function polyPath(pts) {
    return pts.map((p, i) => (i === 0 ? "M" : "L") + `${p.x},${p.y}`).join(" ") + " Z";
}

// 网格线
let gridLines = "";
for (let r of [35, 70, 105, 140]) {
    let gpts = angles.map((_, i) => pt(r, i));
    gridLines += `<path d="${polyPath(gpts)}" fill="none" stroke="#e5e7eb" stroke-width="1"/>`;
}

// 轴线
let axes = "";
for (let i = 0; i < 5; i++) {
    let p = pt(maxR, i);
    axes += `<line x1="${cx}" y1="${cy}" x2="${p.x}" y2="${p.y}" stroke="#d1d5db" stroke-width="1" stroke-dasharray="4,4"/>`;
}

// 标签
let labels = "";
const offsets = [[0, -20], [45, 5], [30, 25], [-30, 25], [-45, 5]];
for (let i = 0; i < 5; i++) {
    let p = pt(maxR, i);
    let text = data.dimensions[i].name + " " + data.dimensions[i].score;
    labels += `<text x="${p.x + offsets[i][0]}" y="${p.y + offsets[i][1]}" text-anchor="middle" font-size="13" fill="#374151" font-family="sans-serif">${text}</text>`;
}

// 数据点
let dataPts = data.dimensions.map((d, i) => pt(maxR * d.score / 100, i));
let targetPts = data.dimensions.map((_, i) => pt(maxR * data.target / 100, i));

let dots = "";
for (let i = 0; i < 5; i++) {
    dots += `<circle cx="${dataPts[i].x}" cy="${dataPts[i].y}" r="5" fill="#2563eb"/>`;
}

const svg = `<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
    <rect width="100%" height="100%" fill="white"/>
    <text x="${cx}" y="30" text-anchor="middle" font-size="18" font-weight="bold" fill="#1e40af">${data.title}</text>
    ${gridLines}
    ${axes}
    <path d="${polyPath(targetPts)}" fill="none" stroke="#f59e0b" stroke-width="2" stroke-dasharray="6,4"/>
    <path d="${polyPath(dataPts)}" fill="#93c5fd" fill-opacity="0.5" stroke="#3b82f6" stroke-width="2.5"/>
    ${dots}
    ${labels}
    <text x="20" y="${height - 40}" font-size="12" fill="#6b7280">蓝色区域 = 当前得分</text>
    <text x="20" y="${height - 20}" font-size="12" fill="#6b7280">橙色虚线 = 目标 L3「75分」</text>
</svg>`;

dv.paragraph(svg);
```
