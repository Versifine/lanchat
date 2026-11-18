# LanChat API 契约 v1

> 本文件是前后端**唯一共享契约**。改动接口/字段/消息类型必须：① 同步改两端实现；② 更新本文档；③ 在 `docs/SPEC.md` 变更记录留痕。

## 1. 通用约定

- Base URL：开发态 `http://localhost:8081`（Vite 代理），生产态由后端同端口托管前端。
- 编码：UTF-8，JSON，字段 camelCase。
- 时间戳：epoch 毫秒（`long`）。
- 认证：REST 请求带 `Authorization: Bearer <JWT>`；WebSocket 用查询参数 `?token=<JWT>`。
- REST 错误响应统一格式：`{"code": <int>, "message": "<中文说明>"}`，HTTP 状态码与业务码并存。
- 分页：`limit` 默认 50，最大 200。

### 错误码

| code | 含义 | HTTP |
|---|---|---|
| 1001 | 未认证/未登录 | 401 |
| 1002 | 用户名已存在 | 409 |
| 1003 | 用户名或密码错误 | 401 |
| 1004 | 参数非法（格式/长度） | 400 |
| 1005 | 用户不存在 | 404 |
| 1006 | 文件过大 | 413 |
| 1007 | 文件不存在/无权访问 | 404 |
| 1008 | 消息过长 | 400 |
| 1009 | 请求过于频繁 | 429 |
| 1010 | 文件校验失败 | 400 |
| 1011 | 协议错误 | 400 |
| 1012 | 服务器内部错误 | 500 |

## 2. 认证 API

### POST /api/auth/register
```json
{"username": "alice", "password": "abc123", "nickname": "爱丽丝"}
```
成功 `200`：
```json
{"token": "<JWT>", "user": {"id": 1, "username": "alice", "nickname": "爱丽丝"}}
```
失败：`409 1002`（重名）、`400 1004`（格式）。

### POST /api/auth/login
```json
{"username": "alice", "password": "abc123"}
```
成功 `200`：同注册返回结构。失败 `401 1003`；`429 1009`（失败过频）。

### GET /api/auth/me
成功 `200`：`{"id":1,"username":"alice","nickname":"爱丽丝"}`。

## 3. 用户 API

### GET /api/users
返回全部用户及在线状态（在线状态来自 WS 在线表）：
```json
[{"id":1,"username":"alice","nickname":"爱丽丝","online":true},
 {"id":2,"username":"bob","nickname":"鲍勃","online":false}]
```

## 4. 消息历史 API

### GET /api/messages/group?limit=50
```json
[{"id":10,"sender":{"id":1,"username":"alice","nickname":"爱丽丝"},
  "receiverId":null,"content":"大家好","createdAt":1763432000123}]
```
按时间升序。

### GET /api/messages/private/{peerId}?limit=50
与 `peerId` 的私聊历史（双向），结构同上，`receiverId` 非空，按时间升序。

## 5. 文件 API

### POST /api/files（multipart/form-data）
| 字段 | 类型 | 说明 |
|---|---|---|
| file | 二进制 | 必填，≤20MB |
| to | long | 选填，接收方用户 id；填了则服务端 WS 通知对方 |

成功 `200`：
```json
{"file":{"id":7,"originalName":"report.pdf","size":1048576,
         "sha256":"<hex>","downloadUrl":"/api/files/7/download"}}
```
失败：`413 1006`、`400 1004`（文件名非法）、`401 1001`。

### GET /api/files/{id}/download
返回文件流，`Content-Disposition: attachment; filename="..."`。需登录；文件不存在 `404 1007`。

### GET /api/files/{id}
返回文件元数据（直接返回 `file` 对象本身，不再包一层 `{"file":...}`；与 POST 响应中的 `file` 字段结构一致）。

> 实现说明：`/api/files/{id}/download` 额外兼容 `?token=<JWT>` 查询参数（便于浏览器直接打开下载链接），
> 前端仍按契约使用 `Authorization: Bearer` 获取 blob 下载。

## 6. WebSocket 契约

- 地址：`ws://localhost:8081/ws/chat?token=<JWT>`
- 握手校验 token 失败：HTTP 401（浏览器表现为连接失败）。
- 双方消息均为 JSON 文本帧，最大 16KB。

### 客户端 → 服务端

| type | 字段 | 说明 |
|---|---|---|
| chat | to: `long \| null`，content: string | to 为 null 表示群聊；否则私聊 |
| ping | 无 | 心跳，服务端回 pong |

```json
{"type":"chat","to":2,"content":"晚上开黑吗"}
{"type":"ping"}
```

### 服务端 → 客户端

| type | 字段 | 说明 |
|---|---|---|
| message | id, sender:{id,username,nickname}, receiverId:null\|long, content, createdAt, offline:bool | 消息推送（含离线补发，offline=true） |
| userList | users:[{id,username,nickname,online}] | 登录后全量 + 在线变化时推送 |
| file | file:{id,originalName,size,sha256,downloadUrl}, sender:{...}, receiverId | 有人给你发了文件 |
| system | message | 系统公告/服务端关闭 |
| pong | timestamp | 心跳应答 |
| error | code, message | 业务错误（如 1009 限流、1008 消息超长） |

> 实现说明：
> 1. 私聊消息同时推送给**发送者与接收者**（发送者据此拿到服务端 id 与顺序，前端不做乐观发送）。
> 2. 消息超长（1008）与高频限流（1009）只返回 `error` 帧，**不主动断开**连接。
> 3. 同一账号重复登录时，旧连接收到 `system` 帧后以 close 1001 关闭。

```json
{"type":"message","id":11,
 "sender":{"id":2,"username":"bob","nickname":"鲍勃"},
 "receiverId":null,"content":"来了","createdAt":1763432000456,"offline":false}
```

### 连接生命周期

| 事件 | 行为 |
|---|---|
| 握手成功 | 服务端注册在线、广播新的 userList、补发未投递私聊；同一账号重复登录会踢掉旧连接并推 `system` |
| 客户端 10s 未 ping | 服务端 35s 无任何消息则关闭连接（code 1001） |
| 服务端关闭 | 推送 `system` 后关闭连接 |
| 前端断线 | 指数退避重连（1s/2s/4s/8s/16s/30s 封顶）；token 过期（4401）则回登录页 |

### 关闭码

| code | 含义 |
|---|---|
| 1000 | 正常关闭 |
| 1001 | 心跳超时/服务端关闭 |
| 1008 | 策略违规（消息超长、限流） |
| 1011 | 服务端内部错误 |
| 4401 | 认证失败（token 无效/过期） |

## 7. 兼容性

- 当前版本 `v1`；字段只增不改，删除字段需升版本并在 SPEC 留痕。
- Swagger UI：`/swagger-ui.html`（开发/演示环境开放）。
