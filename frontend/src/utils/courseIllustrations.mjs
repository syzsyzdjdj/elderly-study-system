// Original vector illustrations authored for these lessons. No external images,
// fonts, scripts or application screenshots are embedded in the SVG source.
function illustration(title, color, drawing) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="460" viewBox="0 0 800 460">
    <rect width="800" height="460" rx="24" fill="#f0f6f2"/>
    <circle cx="730" cy="50" r="130" fill="${color}" opacity=".08"/>
    <text x="40" y="58" fill="#214b38" font-size="30" font-family="sans-serif" font-weight="bold">${title}</text>
    ${drawing}
    <text x="40" y="426" fill="#506d5b" font-size="19" font-family="sans-serif">课程主题示意图 · 非应用界面截图</text>
  </svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

const phone = `<rect x="300" y="88" width="180" height="286" rx="26" fill="white" stroke="#367c65" stroke-width="7"/>
  <path d="M355 105h70m-65 250h60" stroke="#367c65" stroke-width="6" stroke-linecap="round"/>`
const play = `<rect x="322" y="130" width="136" height="160" rx="14" fill="#d9ece4"/>
  <circle cx="390" cy="210" r="42" fill="#367c65"/><path d="m380 185 33 25-33 25z" fill="white"/>`
const headphones = `<path d="M337 235v-45a53 53 0 0 1 106 0v45" fill="none" stroke="#32769a" stroke-width="12"/>
  <rect x="324" y="210" width="30" height="60" rx="13" fill="#32769a"/><rect x="426" y="210" width="30" height="60" rx="13" fill="#32769a"/>`
const person = `<circle cx="390" cy="143" r="29" fill="#eac8ac"/>
  <path d="M368 120q25-20 45 3" stroke="#83958b" stroke-width="12" fill="none" stroke-linecap="round"/>
  <path d="M389 188v90m0-58-67-25m67 25 68 35m-68 23-41 76m41-76 45 76" stroke="#3d8065" stroke-width="23" fill="none" stroke-linecap="round"/>
  <path d="M315 367h150" stroke="#8bae99" stroke-width="5" stroke-linecap="round"/>`
const plate = `<ellipse cx="390" cy="240" rx="168" ry="125" fill="white" stroke="#afc9b6" stroke-width="8"/>
  <ellipse cx="390" cy="240" rx="144" ry="101" fill="#f6f9ef"/>
  <path d="M390 145v190m-140-95h280" stroke="#d2ddc9" stroke-width="3"/>
  <circle cx="323" cy="193" r="29" fill="#77ac64"/><circle cx="303" cy="216" r="24" fill="#4d985a"/>
  <ellipse cx="458" cy="195" rx="42" ry="25" fill="#e7c382"/>
  <ellipse cx="321" cy="286" rx="42" ry="25" fill="#b88652"/>
  <ellipse cx="456" cy="283" rx="38" ry="28" fill="#f2d5ae"/>
  <text x="590" y="185" font-size="23" fill="#3c6449" font-family="sans-serif">蔬果</text>
  <text x="590" y="235" font-size="23" fill="#3c6449" font-family="sans-serif">谷物</text>
  <text x="590" y="285" font-size="23" fill="#3c6449" font-family="sans-serif">蛋白质食物</text>`

export const courseIllustrations = Object.freeze({
  '/uploads/images/course-18-a.svg': illustration('晨练与户外活动', '#367c65',
    `<path d="M90 360h620" stroke="#a5c8b1" stroke-width="8"/>
     <path d="M130 350V210m520 140V210" stroke="#8b7558" stroke-width="13"/>
     <circle cx="130" cy="183" r="55" fill="#b8d9b0"/><circle cx="650" cy="183" r="55" fill="#b8d9b0"/>${person}`),
  '/uploads/images/course-18-b.svg': illustration('太极与舒展活动', '#367c65', person),
  '/uploads/images/course-19-a.svg': illustration('早餐食物搭配', '#b78239',
    `<ellipse cx="310" cy="300" rx="120" ry="25" fill="#b5ccbb"/>
     <path d="M205 225h210q-10 90-105 90t-105-90" fill="#fff" stroke="#729d83" stroke-width="6"/>
     <ellipse cx="310" cy="225" rx="105" ry="26" fill="#e7c382"/>
     <rect x="490" y="145" width="80" height="155" rx="12" fill="#fff" stroke="#729d83" stroke-width="6"/>
     <ellipse cx="620" cy="275" rx="34" ry="47" fill="#efd2ab"/>
     <circle cx="165" cy="290" r="38" fill="#ce7667"/>
     <text x="265" y="350" font-size="23" fill="#3c6449" font-family="sans-serif">谷物</text>
     <text x="490" y="350" font-size="23" fill="#3c6449" font-family="sans-serif">奶蛋</text>`),
  '/uploads/images/course-19-b.svg': illustration('多样食物与营养搭配', '#b78239', plate),
  '/uploads/images/course-20-a.svg': illustration('用手机观看短视频', '#367c65', `${phone}${play}
    <path d="M570 170v100m-15-84 15-16 15 16m-30 68 15 16 15-16" fill="none" stroke="#729d83" stroke-width="6" stroke-linecap="round"/>
    <text x="530" y="320" font-size="23" fill="#3c6449" font-family="sans-serif">上下滑动</text>`),
  '/uploads/images/course-20-b.svg': illustration('线上欣赏传统戏曲', '#ac5d6c',
    `<rect x="175" y="105" width="450" height="265" rx="18" fill="#fff" stroke="#bc8590" stroke-width="6"/>
     <path d="M175 105h85l-35 230h-50zm450 0h-85l35 230h50z" fill="#bd6f7c"/>
     <path d="M335 160q55-65 110 0v75q-7 70-55 90-48-20-55-90z" fill="#f6e7db" stroke="#a95568" stroke-width="5"/>
     <path d="M350 185q20-24 33 13m47-13q-20-24-33 13" stroke="#9d4d60" stroke-width="12" fill="none"/>
     <path d="M355 218h22m26 0h22m-48 61q13 10 26 0" stroke="#584443" stroke-width="6" fill="none" stroke-linecap="round"/>
     <path d="M390 215v38" stroke="#9d4d60" stroke-width="5"/>`),
  '/uploads/images/course-21-a.svg': illustration('用手机听书', '#32769a', `${phone}${headphones}`),
  '/uploads/images/course-21-b.svg': illustration('浏览正规新闻', '#32769a', `${phone}
    <rect x="320" y="133" width="140" height="180" rx="10" fill="#e4eef5"/>
    <text x="345" y="170" font-size="25" fill="#32769a" font-family="sans-serif">新闻</text>
    <path d="M340 195h100m-100 25h100m-100 25h100m-100 25h65" stroke="#7ca3bb" stroke-width="8" stroke-linecap="round"/>`)
})

export function resolveCourseImage(src, resolveMedia) {
  return courseIllustrations[src] || resolveMedia(src)
}
