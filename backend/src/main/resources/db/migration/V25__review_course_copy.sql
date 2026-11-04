-- V25: 课程文字核对；只替换已识别的旧句，保留图片与管理员其他编辑。

-- 核验日期 2026-11-03；依据和适用范围见 docs/course-text-review.md。



UPDATE `course_info` SET `content` = REPLACE(`content`, '每年都要做一次养老金资格认证，证明自己健在，才能继续领取养老金。', '待遇资格认证用于核实是否符合继续领取待遇的条件。认证周期、是否已通过数据比对完成认证及补办要求，以待遇发放地社保经办机构通知为准。') WHERE `id` = 1;

UPDATE `course_info` SET `content` = REPLACE(`content`, '以前要去社区排队办理，现在用手机几分钟就能完成，不用出门。', '可通过当地支持的官方线上渠道办理；线上操作不便时，可咨询社保经办机构的线下协助服务。') WHERE `id` = 1;

UPDATE `course_info` SET `content` = REPLACE(`content`, '可以让子女在他们的手机上帮忙认证，添加亲情账号即可。', '可请家人通过官方亲情服务协助办理，具体渠道以当地支持范围为准；需要人脸识别时必须由待遇领取人本人完成。') WHERE `id` = 1;

UPDATE `course_info` SET `content` = REPLACE(`content`, '每年认证时间以当地通知为准，一般是一年一次，别错过。', '先查询本人最近一次认证记录和有效期，再按待遇发放地要求办理；不必重复已完成的认证。') WHERE `id` = 1;

UPDATE `course_info` SET `content` = REPLACE(`content`, '医保电子凭证就是电子版的医保卡，看病买药刷手机就行，不用带实体卡。', '医保电子凭证也称医保码，是办理医保业务的电子身份凭证。在支持医保码的定点医药机构，可按规定使用本人医保码办理结算。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '全国通用，安全方便，是医保线上业务的唯一身份凭证。', '医保码可跨渠道使用；能否结算、支付范围和待遇标准，还取决于参保状态、机构联网情况与当地医保政策。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '准备好身份证和社保卡，确认本人实名注册过微信或支付宝。', '准备本人身份信息及可接收验证码的手机，使用国家医保服务平台等官方渠道。申领医保码不以持有实体社保卡为前提。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '打开微信，搜索国家医保局或医保电子凭证小程序。', '从国家医保局官网指引进入国家医保服务平台，或使用其公布的微信、支付宝等激活渠道，先核对平台身份。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '验证通过后设置密码，激活成功，凭证会保存在微信卡包里。', '根据所用渠道完成验证和密码等设置，确认页面显示已激活；之后从该渠道的医保码入口使用。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '去医院看病时，打开微信卡包找到医保电子凭证，出示二维码扫码即可。', '在支持医保码的定点医院，从官方渠道打开本人的动态医保码，按工作人员提示办理结算；不要用截图代替动态码。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '去药店买药也一样，出示二维码就能结算，非常方便。', '定点药店是否支持医保码及所购药品能否使用医保支付，以药店和参保地政策为准。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '换了手机不用重新激活，登录微信后凭证会自动同步过来。', '换手机后，从官方渠道重新登录并按要求验证身份，再检查医保码是否可用；不要假定所有渠道都会自动同步。') WHERE `id` = 2;

UPDATE `course_info` SET `content` = REPLACE(`content`, '免费使用，只要有网络就能打，比打电话更亲切。', '双方都需联网并使用微信。通话会消耗网络流量，使用移动网络时可能产生运营商流量费用。') WHERE `id` = 3;

UPDATE `course_info` SET `content` = REPLACE(`content`, '光线不好时可以切换摄像头，点翻转图标用后摄像头拍风景。', '想让家人看周围环境时，可点翻转图标切换摄像头；光线不足时应先改善照明。') WHERE `id` = 3;

UPDATE `course_info` SET `content` = REPLACE(`content`, '年纪大了眼睛花、耳朵背，手机默认字体小、声音小，看着费劲。', '如果觉得手机文字太小或声音不清楚，可以根据自己的需要调整显示与声音设置。') WHERE `id` = 4;

UPDATE `course_info` SET `content` = REPLACE(`content`, '按手机侧面的音量键，把铃声调大，能清楚听到来电。', '侧面音量键控制的声音类型随手机和使用状态而变；调整来电铃声时，先到设置中确认铃声音量。') WHERE `id` = 4;

UPDATE `course_info` SET `content` = REPLACE(`content`, '在设置里找到声音，把铃声、闹钟、媒体音量都调大。', '在声音设置中分别检查铃声、闹钟和媒体音量，从较低音量逐步调整到合适程度。') WHERE `id` = 4;

UPDATE `course_info` SET `content` = REPLACE(`content`, '放大功能点三下屏幕就能放大字，随选朗读能让手机读出文字。', '先查看本机放大与朗读功能的操作说明并设置快捷方式；触发手势和支持范围因品牌、系统版本而异。') WHERE `id` = 4;

UPDATE `course_info` SET `content` = REPLACE(`content`, '骗子最爱盯老人，电话、短信、网络处处是陷阱，一不留神钱就没了。', '诈骗可能通过电话、短信或网络发生。认识常见话术，遇到催促转账的情况先停下来核实。') WHERE `id` = 5;

UPDATE `course_info` SET `content` = REPLACE(`content`, '三、真实案例警示', '三、情景练习（虚构示例）') WHERE `id` = 5;

UPDATE `course_info` SET `content` = REPLACE(`content`, '老人被骗真实案例', '防骗情景练习（虚构示例）') WHERE `id` = 5;

UPDATE `course_info` SET `content` = REPLACE(`content`, '拿不准的事，先打110报警或问子女，千万别自己急着转账。', '拿不准时先通过原有联系方式向家人或相关机构核实；如已转账或疑似被骗，保留证据并及时报警。') WHERE `id` = 5;

UPDATE `course_info` SET `content` = REPLACE(`content`, '手机挂号不用去医院排队，在家就能挂上号，省时省力。', '手机预约可减少现场挂号等候，但能否预约取决于医院号源；到院后仍需按要求签到、候诊。') WHERE `id` = 6;

UPDATE `course_info` SET `content` = REPLACE(`content`, '打开微信，搜索要去的医院公众号或小程序，关注后进入。', '先从医院官网或院内公告确认官方公众号、小程序或应用入口，避免进入冒名平台。') WHERE `id` = 6;

UPDATE `course_info` SET `content` = REPLACE(`content`, '填好就诊人信息，确认后提交，付款成功就挂号完成了。', '核对就诊人、院区、科室和时间，按页面要求提交及支付，再到预约记录确认成功状态。') WHERE `id` = 6;

UPDATE `course_info` SET `content` = REPLACE(`content`, '就诊当天提前到院，用身份证或医保卡在自助机取号。', '按预约记录的要求携带证件、提前到院；是否需要取号、在哪里签到，以医院通知为准。') WHERE `id` = 6;

UPDATE `course_info` SET `content` = REPLACE(`content`, '去不了要及时取消，在公众号里点我的挂号取消，把号让给别人。', '不能按时就诊时，及时查看医院的取消时限、退费和爽约规则，从预约记录办理取消。') WHERE `id` = 6;

UPDATE `course_info` SET `content` = REPLACE(`content`, '不要信黄牛代挂，加价挂号都是骗局，有号也白搭。', '通过医院公布的正规渠道预约，不轻信黄牛代挂，不向陌生人提供身份信息或转账。') WHERE `id` = 6;

UPDATE `course_info` SET `content` = REPLACE(`content`, '办了备案，在外地医院看病能直接刷卡结算，不用来回跑报销。', '按规定备案后，在已开通相应异地结算服务的联网定点医药机构，可按参保地政策办理直接结算；并非所有机构、费用都支持。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '这些情况都要先办备案，不备案在外地看病报销麻烦。', '跨省长期居住、转诊等情况应先了解参保地备案要求；省内异地就医规定可能不同，异地急诊抢救按规定视同已备案，不要为办手续延误救治。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '打开微信搜索国家异地就医备案小程序，进入后选快速备案。', '从国家医保服务平台等官方渠道进入异地就医备案服务，按参保地开放的入口办理。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '上传身份证或承诺书，人脸识别确认后提交，等审核通过。', '按本人备案类型和参保地要求提交材料、完成身份验证，再查询备案结果；不能把某一种材料视为全国统一要求。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '备案通过后，在外地定点医院看病，出示医保电子凭证就能直接结算。', '备案后先确认有效期和就医地，查询医院是否开通所需业务的跨省联网结算；就诊时按要求使用医保码或社保卡等有效凭证。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '个人只付该付的部分，报销部分医院直接扣，不用拿回老家报。', '结算成功后按结算单支付个人负担部分；无法直接结算时，保存票据及材料，向参保地咨询报销流程。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '备案一般两三个工作日就通过，急用可办紧急备案。', '办理时限因地区、备案类型和材料情况而异，以官方办理页为准；急诊抢救应先及时就医。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '备案有有效期，到期或换地方要重新办，别忘记续。', '跨省异地长期居住人员备案后，未变更备案信息或参保状态时，备案按规定长期有效；临时外出及变更要求向参保地确认。') WHERE `id` = 7;

UPDATE `course_info` SET `content` = REPLACE(`content`, '电子社保卡是实体社保卡的线上版，扫码就能用，不用带卡出门。', '电子社保卡是社保卡线上应用的有效电子凭证，支持身份认证、信息查询等服务；具体可用功能以当地开通情况为准。') WHERE `id` = 8;

UPDATE `course_info` SET `content` = REPLACE(`content`, '和实体卡功能一样，还能查社保、办业务，更方便。', '电子社保卡、实体社保卡和医保码的使用场景不完全相同，不能认为任一电子码都能办理全部业务。') WHERE `id` = 8;

UPDATE `course_info` SET `content` = REPLACE(`content`, '要先有实体社保卡，并且已经激活过。', '先确认社保卡状态正常，并按官方申领页要求核验身份；社保功能与银行金融功能应分别了解，领取电子卡不等于激活银行账户。') WHERE `id` = 8;

UPDATE `course_info` SET `content` = REPLACE(`content`, '验证通过后设置密码，领取成功，卡面保存在微信卡包里。', '按官方页面完成验证和密码设置，确认领取成功后，从所用渠道的电子社保卡入口查看。') WHERE `id` = 8;

UPDATE `course_info` SET `content` = REPLACE(`content`, '三是办失业工伤待遇，四是扫码就医购药，出示二维码就行。', '还可查看当地已开通的待遇申领等服务。就医购药结算应使用医药机构支持的医保码、社保卡等凭证。') WHERE `id` = 8;

UPDATE `course_info` SET `content` = REPLACE(`content`, '密码不要告诉别人，手机丢了要尽快挂失补办社保卡。', '密码不要告诉他人。手机丢失后，及时挂失手机卡并处理相关账户风险；实体社保卡或金融账户是否需挂失，向对应机构核实。') WHERE `id` = 8;

UPDATE `course_info` SET `content` = REPLACE(`content`, '输入金额和个数，写上祝福语，点塞钱进红包。', '给一位联系人发红包时核对收款人和金额；群红包还需核对个数及分配方式，再按页面提示确认。') WHERE `id` = 9;

UPDATE `course_info` SET `content` = REPLACE(`content`, '输入金额，输支付密码确认，钱就付好了。', '核对收款方和订单金额，再按页面提示完成支付；返回账单确认状态，不要仅凭扣款提示重复付款。') WHERE `id` = 9;

UPDATE `course_info` SET `content` = REPLACE(`content`, '不要扫陌生人发的二维码，可能有病毒或盗刷风险。', '不明二维码可能引导至钓鱼、付款或下载页面；扫码后也要核对内容，不随意授权、安装或付款。') WHERE `id` = 9;

UPDATE `course_info` SET `content` = REPLACE(`content`, '点相机左下角小图，或打开相册APP，就能看到所有照片。', '点相机画面中的照片缩略图，或打开相册查看照片；缩略图位置与可见范围因手机和权限设置而异。') WHERE `id` = 10;

UPDATE `course_info` SET `content` = REPLACE(`content`, '不要的照片长按选中，点删除就能删掉，腾出空间。', '确认不再需要后再删除照片；部分手机会先移入“最近删除”，彻底删除前再次核对，并了解能否恢复。') WHERE `id` = 10;

UPDATE `course_info` SET `content` = REPLACE(`content`, '重要的照片发给子女让他们存好，或备份到云空间，本地就能删。', '重要照片先备份，并在另一设备确认可打开。云相册可能同步删除，删除本地照片前先核对同步规则，不把聊天转发视为可靠的原图备份。') WHERE `id` = 10;

UPDATE `course_info` SET `content` = REPLACE(`content`, '做好安全设置，即使手机丢了别人也动不了你的钱。', '安全设置能降低风险，不能保证资金绝对安全；手机丢失后仍需及时处理手机卡、微信和银行卡等账户风险。') WHERE `id` = 11;

UPDATE `course_info` SET `content` = REPLACE(`content`, '按提示录入指纹或刷脸，以后付款用指纹刷脸更安全方便。', '按设备提示核验已登记的指纹或面容，开启前先了解验证方式；每次支付仍要核对收款方和金额。') WHERE `id` = 11;

UPDATE `course_info` SET `content` = REPLACE(`content`, '四、设置支付限额', '四、查看支付限额与验证方式') WHERE `id` = 11;

UPDATE `course_info` SET `content` = REPLACE(`content`, '在支付设置里找到转账到账时间和额度设置。', '从微信支付官方帮助或银行卡官方渠道查询限额、验证方式和当前规则；不同账户不一定有相同的自定义限额入口。') WHERE `id` = 11;

UPDATE `course_info` SET `content` = REPLACE(`content`, '把单笔和单日限额调小一些，即使被盗刷损失也有限。', '如账户支持，可按需要设置较小限额；延时到账不代表交易一定可撤回，不能代替付款前核实。') WHERE `id` = 11;

UPDATE `course_info` SET `content` = REPLACE(`content`, '定期看账单里的消费记录，发现不明支出马上改密码。', '定期核对账单。发现不明支出，及时联系支付平台、银行处理账户风险，保留交易证据；疑似诈骗时及时报警。') WHERE `id` = 11;

UPDATE `course_info` SET `content` = REPLACE(`content`, '一、为什么骗子专盯老人', '一、为什么要警惕保健品推销') WHERE `id` = 12;

UPDATE `course_info` SET `content` = REPLACE(`content`, '老人怕生病、爱惜命、又有积蓄，容易被养生忽悠。', '骗子可能利用人们对健康的关切，夸大产品功效，或以免费体验、讲座等方式推销。') WHERE `id` = 12;

UPDATE `course_info` SET `content` = REPLACE(`content`, '子女不在身边没人商量，骗子就趁虚而入套近乎。', '购买前先核实资质和宣传内容；拿不准时可向医生、药师或信任的人咨询。') WHERE `id` = 12;

UPDATE `course_info` SET `content` = REPLACE(`content`, '第二招看价格，几千上万的产品正常药店几百块就能买到。', '第二招看宣传：保健食品不能代替药物治疗疾病，价格高低或有“蓝帽”标志都不能证明有治病效果。') WHERE `id` = 12;

UPDATE `course_info` SET `content` = REPLACE(`content`, '四、真实案例警示', '四、情景练习（虚构示例）') WHERE `id` = 12;

UPDATE `course_info` SET `content` = REPLACE(`content`, '别去参加免费讲座和旅游，免费的往往最贵。', '警惕借免费讲座或旅游推销产品、要求当场交钱的活动；不要因赠品或限时优惠匆忙购买。') WHERE `id` = 12;

UPDATE `course_info` SET `content` = REPLACE(`content`, '网上假消息满天飞，老人分辨不清容易信，转发还害别人。', '网络消息真假混杂，任何人都可能被误导。转发前核对来源、时间和完整内容，可以减少误传。') WHERE `id` = 13;

UPDATE `course_info` SET `content` = REPLACE(`content`, '第三步问子女，拿不准就发给子女问问，年轻人会查。', '第三步请人一起查，拿不准时请信任的人协助核实；不能仅凭年龄或转发人的身份判断真假。') WHERE `id` = 13;

UPDATE `course_info` SET `content` = REPLACE(`content`, '某种食物致癌、某偏方能治百病、某地发生了什么大事，多半是假的。', '健康风险、偏方疗效和突发事件都需要具体证据。不要只看标题就认定真假，应核对权威原始信息和适用条件。') WHERE `id` = 13;

UPDATE `course_info` SET `content` = REPLACE(`content`, '要求转发给多少人否则会怎样的，百分百是谣言，别信。', '以“不转发就会出事”等话术施压，是应当警惕的信号；不要按要求转发，先核实内容。') WHERE `id` = 13;

UPDATE `course_info` SET `content` = REPLACE(`content`, '刷单是虚假交易，违反电商法和反不正当竞争法，是违法行为。', '虚构交易、编造评价可能涉及违法违规，不要参与；具体责任需依据行为和现行规定判断。') WHERE `id` = 14;

UPDATE `course_info` SET `content` = REPLACE(`content`, '凡是先垫钱后返利的，全是诈骗，遇到就拉黑报警。', '遇到要求先转账垫资、承诺刷单返利或以“解冻”名义催款的任务，立即停止；保留聊天和转账记录，疑似被骗时及时报警。') WHERE `id` = 14;

UPDATE `course_info` SET `content` = REPLACE(`content`, '不受时间地点限制，晚上也能交，不用跑腿。', '可在服务开放且网络正常时线上缴费；遇到维护或当地不支持的项目，可联系缴费单位选择其他渠道。') WHERE `id` = 15;

UPDATE `course_info` SET `content` = REPLACE(`content`, '选要交的项目，水费、电费、燃气费都有，输入户号。', '选择当地已开通的项目，核对城市和缴费单位，照自家缴费单输入户号。') WHERE `id` = 15;

UPDATE `course_info` SET `content` = REPLACE(`content`, '查到欠费金额，输支付密码确认，就交好了。', '核对户名、户号和金额，再按页面提示支付，并到缴费记录确认结果；处理中不要重复支付。') WHERE `id` = 15;

UPDATE `course_info` SET `content` = REPLACE(`content`, '可以在缴费页设自动缴费，到期自动扣，不用记日期。', '如项目支持自动缴费，开通前先阅读扣费方式、条件和取消规则；也可只设置日历提醒，不开通自动扣费。') WHERE `id` = 15;

UPDATE `course_info` SET `content` = REPLACE(`content`, '缴费后一般一两分钟到账，没到账可查缴费记录或打客服。', '到账和复供时间以缴费单位说明为准；未到账时先查订单状态，并通过官方渠道联系客服，不要连续重复付款。') WHERE `id` = 15;

UPDATE `course_info` SET `content` = REPLACE(`content`, '看车牌和司机信息，到楼下上车，到达后自动扣款。', '先核对车牌、车型和司机信息，再到约定的安全地点上车；到达后核对账单，是否自动扣款取决于是否已授权。') WHERE `id` = 16;

UPDATE `course_info` SET `content` = REPLACE(`content`, '在乘车码小程序里开通当地公交乘车码，上车刷码就行。', '先确认当地公交、地铁支持的乘车码，阅读扣费规则后按需开通；进出站或上下车刷码要求以运营方为准。') WHERE `id` = 16;

UPDATE `course_info` SET `content` = REPLACE(`content`, '很多城市有老人免费或半价乘车政策，带老年证或开通电子卡。', '老年人乘车优惠的年龄、证件、线路和办理要求因城市而异；普通乘车码不一定享受优惠，出行前向当地运营方核实。') WHERE `id` = 16;

UPDATE `course_info` SET `content` = REPLACE(`content`, '慢性病复诊开药、小毛病咨询、不愿去医院排队，适合在线问诊。', '互联网诊疗主要用于符合条件的部分常见病、慢性病复诊。应提供既往明确诊断的病历，由接诊医师判断是否适合线上诊疗；新出现的症状不能仅因不想排队就在线开药。') WHERE `id` = 17;

UPDATE `course_info` SET `content` = REPLACE(`content`, '打开微信或支付宝，搜互联网医院或医院的线上问诊。', '从医院官网等官方渠道确认互联网医院入口和医疗机构资质，不从不明广告链接进入。') WHERE `id` = 17;

UPDATE `course_info` SET `content` = REPLACE(`content`, '医生开方后在线付款，选送药上门或就近药店自取。', '医生根据病情决定是否开具处方，经审核后按平台支持范围购药或取药；问诊不保证一定开药。') WHERE `id` = 17;

UPDATE `course_info` SET `content` = REPLACE(`content`, '在美团或饿了么搜买药，选附近药店下单，外卖送药上门。', '通过正规平台选择资质齐全的药店，核对药名、规格和用法；配送范围、时间和费用以订单为准。') WHERE `id` = 17;

UPDATE `course_info` SET `content` = REPLACE(`content`, '处方药要有医生处方才能买，非处方药直接下单就行。', '处方药须凭合法有效处方按规定购买；非处方药也需核对适用人群、禁忌及药物相互作用，有疑问先咨询医生或药师。') WHERE `id` = 17;

UPDATE `course_info` SET `content` = REPLACE(`content`, '在线问诊费用比挂号便宜，能用医保的医院更划算。', '问诊、药品和配送费用分别核对；线上价格不一定更低，医保支付资格与范围以参保地和医疗机构规定为准。') WHERE `id` = 17;

UPDATE `course_info` SET `content` = REPLACE(`content`, '送的药和医院一样正规，有质量问题可联系药店退换。', '收药时核对药品包装、有效期和储存要求；发现异常先不要服用，保留凭证并联系药店或平台，按规定处理，不能保证所有药品都可退换。') WHERE `id` = 17;

UPDATE `course_info` SET `content` = REPLACE(`content`, '研究显示，坚持晨练的老年人跌倒风险降低约30%，睡眠质量也有明显提升。', '运动效果因人而异，本课不承诺固定幅度的跌倒风险降低或治疗效果。') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '先慢走五分钟热身，再中等速度走二十到三十分钟，走到微微出汗、能说话不能唱歌的强度正好。', '可从短时间慢走开始，按体力逐渐增加，活动时以呼吸平稳、能交谈为参考，不以出汗或固定时长作为人人适用的标准。') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '其中"开合手"动作简单易学，每天练20-30次，有助于提高肺活量和血氧饱和度。', '应跟随正规八段锦示范学习，不把其他功法动作混入，也不自行按次数追求所谓“提高血氧”的效果。') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '1. 从桩功开始：无极桩、浑圆桩、开合桩是基本功，每天站5-10分钟，培养身体的稳定感和内劲；', '1. 从适合自己的基础动作开始，由合格指导者讲解；不要自行长时间站桩或深蹲；') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '2. 步法五步：转腰、落胯提膝、着地、进步、后退，每步要求轻松自然、虚实分明；', '2. 转身和重心转移应缓慢，保持站稳；平衡较差时先请专业人员评估，避免独自练习复杂步法；') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '4. 运动中若心慌、腿软，立即坐下休息，随身药卡和联系电话要带好。', '4. 运动中若出现胸痛、明显气短、头晕或不稳，立即停止活动并到安全处求助，必要时及时就医；随身携带紧急联系人信息。') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '答：六千步左右即可，不必追求一万步，关节不好的老人减半。', '答：没有适用于所有人的统一步数，应结合体力、疾病和关节情况循序渐进；有明显不适或活动受限时先咨询医生。') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '答：减少时长、放慢速度，疼痛持续就到医院检查，暂时改练八段锦。', '答：先停止引起疼痛的动作，不要未经评估就换成另一套运动；疼痛持续、明显肿胀或行走困难时及时就医。') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '每天练20-30分钟，一两个月就能打完整套。', '练习时长和进度因人而异，先确保动作安全，再逐步学习，不设固定完成期限。') WHERE `id` = 18;

UPDATE `course_info` SET `content` = REPLACE(`content`, '年纪大了牙口松、消化慢、味觉变淡，很多老人吃得越来越咸、越来越单一。', '咀嚼、吞咽能力和食欲会因人而异；选择适合自己的食物质地，保持食物多样。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '吃对了，血压血糖平稳、腿脚有劲；吃错了，营养跟不上还容易吃出毛病。', '合理膳食有助于维持营养和健康；血压、血糖管理还需结合疾病情况和医嘱。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '二、早餐的"黄金公式"', '二、早餐搭配示例') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '<strong>1份蛋白质 + 1份杂粮 + 1份蔬果</strong>', '<strong>主食搭配蛋、奶或豆制品，再配蔬菜或水果；具体份量因人而异。</strong>') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '一个中等大小的鸡蛋、300毫升牛奶或1碗无糖豆浆，即可满足一餐蛋白质需求。', '鸡蛋、牛奶和豆浆的蛋白质含量不同，不能简单互换或认定其中一种就满足整餐需要。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '两餐之间可加一次水果或酸奶，血糖高的老人选苹果、柚子等低糖水果。采用三餐两点制（早、午、晚+上下午各一次加餐）。', '是否加餐及水果份量应结合食量、血糖和用药情况决定，不统一要求三餐两次加餐；服药期间如需选择柚类水果，先咨询药师。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '早餐加1个煮鸡蛋+1杯液体蛋白，午餐50-100克瘦肉，晚餐1份豆腐或50克白肉，保证餐餐都有蛋白质；', '各餐适量搭配蛋、奶、鱼、肉或豆制品；不把来源不明的“液体蛋白”作为日常必需品；') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '4. 每天喝够六七杯水，别等口渴才喝；', '4. 主动少量多次饮水；心、肾疾病等需要限水时按医嘱安排，不套用统一杯数；') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '5. 盐一天不超过一瓶盖（约5克），少吃咸菜、腊肉、酱料。', '5. 一般建议每天食盐不超过5克，含调味品和加工食品中的盐；用限盐勺或称量工具，不用大小不一的瓶盖估算。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '1. 剩菜及时放冰箱，吃前彻底加热，绿叶菜、凉拌菜过夜就倒掉；', '1. 尽量按需制作；剩余熟食及时妥善冷藏，食用前彻底加热，不食用变质或保存不当的食物；') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '3. 冰箱不是保险箱，冷藏超过两三天的食物不再食用；', '3. 冷藏不能保证食品一直安全，保存期限因食品、包装和温度而异，应遵循标签与储存要求；') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '答：老年人每天每公斤体重需1.0-1.2克蛋白质，60公斤的老人一天约60-72克，相当于2个鸡蛋+1杯牛奶+2两肉+1块豆腐。', '答：需要结合体重、营养状况和疾病情况评估，不能把固定食物组合当作所有人的处方；有肾脏疾病、营养不良或吞咽困难时，请医生或临床营养师制定方案。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '3. 首次使用会弹出权限提示，点"允许"即可正常浏览；', '3. 按当前使用功能决定是否授权；单纯浏览时不要一律允许通讯录、定位、摄像头等权限，可先拒绝不需要的权限；') WHERE `id` = 20;

UPDATE `course_info` SET `content` = REPLACE(`content`, '4. 抖音有"长辈模式"：在"我—设置—长辈模式"中开启，字更大、图标更简洁。', '4. 可在应用设置中查找大字、长辈或关怀模式；名称和入口以所装版本为准。') WHERE `id` = 20;

UPDATE `course_info` SET `content` = REPLACE(`content`, '2. 各地戏曲剧院常在网上直播演出，准时进入就能免费看大戏；', '2. 可关注剧院公布的线上演出信息；观看前先确认是否收费，不把所有直播视为免费；') WHERE `id` = 20;

UPDATE `course_info` SET `content` = REPLACE(`content`, '3. 拍完后配上音乐和文字，发布即可和朋友们分享。', '3. 发布前核对可见范围和音乐使用规则，避开身份证、门牌、支付码及他人隐私；取得出镜者同意后再分享。') WHERE `id` = 20;

UPDATE `course_info` SET `content` = REPLACE(`content`, '眼睛花了看小字费劲，但耳朵还灵。', '如果阅读小字不方便，可以按自己的听力和使用习惯选择听书。') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '国内最大的有声平台，搜索功能强大，', '可从官方应用商店了解其服务，') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '专为中老年设计，大字体、语音搜索、一键收听、无广告，拥有文学名著、曲艺戏曲、名家评书、健康养生等上百个类别。', '安装前先在官方应用商店核对开发者、适配情况、广告和付费说明，不把“大字版”名称当作无广告或官方身份的保证。') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '免费听书APP，有大量小说和有声内容。', '提供听书内容，具体节目、广告和付费安排以当前应用说明为准。') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '微信内的小程序，不用单独安装，在微信搜索"听书"即可找到。', '使用前先核实当前产品是否仍提供服务及官方入口；不要把搜索结果中的同名小程序直接当作官方产品。') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '4. 听到哪里退出，下次打开会记住位置接着听；', '4. 如应用支持，可用书签或播放历史保存进度；换设备、清理数据或退出账号后，应重新检查进度；') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '5. 连接蓝牙音箱或小收音机外放，声音更清楚。', '5. 可连接支持相应功能的蓝牙音箱；普通收音机不一定支持连接手机。调节到合适音量，避免影响他人。') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '3. 找不准的字可以长按选择"朗读"，让手机读给您听。', '3. 如应用或系统支持，可使用朗读或无障碍功能；入口与手势以设备说明为准，并非所有文字都能长按朗读。') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '2. 夜里开灯看，黑屋子看屏幕最伤眼；', '2. 保持适宜环境照明，按舒适程度调节屏幕亮度，避免在昏暗环境长时间盯屏；') WHERE `id` = 21;

UPDATE `course_info` SET `content` = REPLACE(`content`, '主食一碗，荤素搭配，鱼、瘦肉、豆腐换着来。午餐50-100克瘦肉，搭配一荤两素。', '主食与鱼、瘦肉、豆制品、蔬菜搭配，具体份量按食量、活动和疾病情况调整。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '七分饱，清淡易消化，一份豆腐或50克白肉（鸡肉、鱼肉、虾肉），睡前两三个小时不再进食。', '晚餐规律、适量，选择易于咀嚼和消化的食物；消瘦或营养不良者不要刻意少吃，需要夜间加餐时按专业建议安排。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '答：做成肉末、肉丸、清蒸鱼，营养不打折。', '答：可尝试细软肉末、去刺鱼肉等，注意防噎；如有吞咽困难、反复呛咳，应先就医评估，不能只靠把食物切碎处理。') WHERE `id` = 19;

UPDATE `course_info` SET `content` = REPLACE(`content`, '八个简单动作十来分钟做完，风雨天在家也能练。', '可在安全、宽敞的场地，跟随正规示范按自己的体力分段学习。') WHERE `id` = 18;
