# 账号与观影同步服务端准备

作者 Rios敖丙。采用 Supabase Auth + Postgres 数据库，当前提供数据库迁移与接口约定，尚未创建云项目、运行迁移或接入各端登录界面。不能将此目录当作已上线的账号系统。

部署：创建 Supabase 项目，在 SQL Editor 执行 `supabase/migrations/001_library.sql`。开启邮箱验证，配置邮件服务、找回密码回跳地址和认证限流。客户端仅使用项目 URL 与公开 publishable/anon key，绝不能打包 service_role/secret key。应用登录令牌存入各系统安全存储，不能放到 GitHub 或日志。

注册、登录、刷新、退出和找回密码使用 Supabase Auth 官方 SDK/REST。数据表开启按 auth.uid() 隔离的 RLS，匿名不能读取记录。上线前必须在真实项目验证两个账号不能互读、不能写入他人 user_id、退出后令牌失效等场景；本地尚未完成数据库执行验证。

同步接口：以用户 Bearer token 请求 `/rest/v1/library_records`。每次登录先全量获取该用户记录（包含 deleted=true 的删除标记），之后可按 updated_at 增量查询并设置至少一分钟重叠窗口、按主键去重。分页时按 updated_at 和主键稳定排序，未取完前不要推进游标；也可以小规模下始终全量同步，避免同时间记录遗漏。

写入使用 PostgREST upsert，冲突键 user_id,kind,content_key，并发送 `Prefer: resolution=merge-duplicates`。kind 为 favorite/history；content_key 建议 SHA256(规范化接口地址 + vod_id)，避免不同源 ID 碰撞。payload 包含 title、poster、source、vodId、episode、positionSeconds、durationSeconds；播放凭据、账号密码与临时签名播放 URL 不应上传。跨源同名内容是否相同需用户确认，不能仅凭名称合并。

取消收藏、删除记录写 deleted=true，避免其它离线设备把删除内容自动恢复。客户端应维护未上传变更队列，成功写入后再清队列；本版冲突策略为最后到达服务端的写入优先，离线旧进度可能覆盖新进度，正式客户端需要展示冲突或比较观影事件时间。updated_at 由数据库设置，不能由客户端指定。

Supabase 免费方案当前有容量/流量额度且低活跃项目可能暂停，不保证永久免费或持续在线。只保存账号和记录，不托管或转发视频。待部署后再配置各平台服务地址并完成实机双设备同步验收。
