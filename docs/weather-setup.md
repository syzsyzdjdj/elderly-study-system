# 天气陪伴与气象预警

## 默认免密钥天气

默认可以直接使用 Open-Meteo，无需注册或 API KEY。未配置和风天气，或和风天气无法获取基础天气时，会自动尝试该备用源。使用省、市匹配市级地点，拒绝同名但省份不匹配的结果；不把市级估算标成区县实况。

显示中文天气、气温、数据源，并基于当前温度、降水和风速生成“生活提醒”。这些提醒与官方气象预警明确区分，不代表气象部门发布预警。天气数据超过两小时不显示为当前天气。

默认免费公共接口仅适合非商业/教学用途，商业部署需遵守 [Open-Meteo 使用条款](https://open-meteo.com/en/terms)。天气数据注明 Open-Meteo/CC BY 4.0，地点数据注明 GeoNames。省市名称会发送给该服务用于匹配地点。

## 可选：启用官方预警

后端使用和风天气的专属 API Host 和 API KEY。请在和风天气控制台创建项目、凭证，并确认账号可访问 GeoAPI、Current Weather 和 Weather Alert 服务。

在启动后端的 PowerShell 中设置（替换占位符，不要提交真实密钥）：

```powershell
$env:QWEATHER_API_HOST = '你的专属域名.qweatherapi.com'
$env:QWEATHER_API_KEY = '你的API密钥'
mvn spring-boot:run
```

在 `backend` 目录启动。Host 只填域名，不带 `https://` 和路径。密钥不会返回前端。未配置凭证不影响免密钥基础天气，但官方预警仍需配置。

用户在“显示与账号设置”中保存省、市、区县后，打开“AI 数字陪伴”即可查看天气、官方预警原文、发布机构、发布时间、有效期和防护提示，并可点击朗读。请求身份由后端登录上下文确定，接口不接受其他用户 ID 或任意地点。

## 提醒行为与范围

- App 打开且页面可见时，登录后和每 5 分钟检查预警；回到前台也会检查，最短间隔 1 分钟。
- 新预警通过 App 内顶部提醒呈现，点击进入 AI 陪伴详情。同一用户、地区、预警 ID 和发布时间去重。
- “天气陪伴”文案基于真实天气和预警组合，不让大模型生成预警事实。预警原文和来源完整保留。
- 地区数据在服务器缓存 5 分钟，失败缓存 1 分钟。未配置、地区不匹配、网络故障、仅天气失败、仅预警失败分别显示对应状态。
- 只使用尚未过期且未被替代、取消的官方预警；接口查询成功无预警，不代表当地绝对安全。
- 这是 App 内提醒，关闭 App 后不会继续检查；不包含 Android 后台推送或短信服务。
- 地区名称会发送给和风天气用于匹配天气地点；不会发送用户姓名、手机号、账号或精确 GPS 定位。

## 接口依据

- [API Host](https://dev.qweather.com/en/docs/configuration/api-host/)
- [认证](https://dev.qweather.com/en/docs/configuration/authentication/)
- [城市查询](https://dev.qweather.com/en/docs/api/geoapi/city-lookup/)
- [当前天气](https://dev.qweather.com/en/docs/api/weather/weather-current/)
- [天气预警](https://dev.qweather.com/en/docs/api/warning/weather-alert/)
