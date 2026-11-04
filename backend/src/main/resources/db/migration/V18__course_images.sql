-- V18: 为课程18-21的图文内容插入SVG配图（与课程封面同款风格）
-- SVG文件存于 uploads/images/ 目录，无外部依赖

-- 课程18：晨练散步与太极入门
-- 在"三、适合老年人的锻炼方法"后插入太极图片，在"四、太极拳入门要点"后插入桩功图片
UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p><strong>3. 八段锦：</strong>八个简单动作十来分钟做完，风雨天在家也能练。其中"开合手"动作简单易学，每天练20-30次，有助于提高肺活量和血氧饱和度。</p>',
  '<p><strong>3. 八段锦：</strong>八个简单动作十来分钟做完，风雨天在家也能练。其中"开合手"动作简单易学，每天练20-30次，有助于提高肺活量和血氧饱和度。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-18-a.svg" alt="老年人在公园晨练打太极拳" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p>5. 练习时全身放松，用意不用力，呼吸自然，切忌憋气。</p>',
  '<p>5. 练习时全身放松，用意不用力，呼吸自然，切忌憋气。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-18-b.svg" alt="太极拳桩功练习" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 18;

-- 课程19：三餐营养与饮食安全
-- 在"二、早餐的黄金公式"后插入早餐图片，在"四、营养搭配要点"后插入营养餐图片
UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p>杂粮可选燕麦粥、小米粥、全麦馒头，搭配一份番茄、黄瓜或苹果。</p>',
  '<p>杂粮可选燕麦粥、小米粥、全麦馒头，搭配一份番茄、黄瓜或苹果。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-19-a.svg" alt="营养早餐搭配示例" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p>5. 盐一天不超过一瓶盖（约5克），少吃咸菜、腊肉、酱料。</p>',
  '<p>5. 盐一天不超过一瓶盖（约5克），少吃咸菜、腊肉、酱料。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-19-b.svg" alt="均衡营养餐盘示例" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 19;

-- 课程20：刷短视频与线上看戏曲
-- 在"二、下载和注册短视频APP"后插入老人看手机图片，在"四、线上看戏曲和大戏"后插入戏曲图片
UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p>4. 抖音有"长辈模式"：在"我—设置—长辈模式"中开启，字更大、图标更简洁。</p>',
  '<p>4. 抖音有"长辈模式"：在"我—设置—长辈模式"中开启，字更大、图标更简洁。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-20-a.svg" alt="老年人用手机看短视频" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 20;

UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p>4. 电视端可让子女帮忙安装戏曲频道应用，投屏到大电视更过瘾。</p>',
  '<p>4. 电视端可让子女帮忙安装戏曲频道应用，投屏到大电视更过瘾。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-20-b.svg" alt="中国传统戏曲表演" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 20;

-- 课程21：手机听书与新闻浏览
-- 在"二、推荐的听书软件"后插入听书图片，在"五、看正规新闻"后插入看新闻图片
UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p>4. <strong>微信听书：</strong>微信内的小程序，不用单独安装，在微信搜索"听书"即可找到。</p>',
  '<p>4. <strong>微信听书：</strong>微信内的小程序，不用单独安装，在微信搜索"听书"即可找到。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-21-a.svg" alt="老年人用手机听书" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`,
  '<p>3. 找不准的字可以长按选择"朗读"，让手机读给您听。</p>',
  '<p>3. 找不准的字可以长按选择"朗读"，让手机读给您听。</p>
<p style="text-align:center;margin:12px 0;"><img src="/uploads/images/course-21-b.svg" alt="老年人用手机看新闻" style="max-width:100%;border-radius:8px;" /></p>'
) WHERE `id` = 21;
