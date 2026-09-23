CREATE TABLE IF NOT EXISTS "User" (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  passwordHash TEXT NOT NULL,
  createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  failedLoginAttempts INTEGER NOT NULL DEFAULT 0,
  lockedUntil DATETIME
);

CREATE TABLE IF NOT EXISTS "Session" (
  token TEXT PRIMARY KEY,
  userId INTEGER NOT NULL,
  expiresAt DATETIME NOT NULL,
  createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "LoginEvent" (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  eventType TEXT NOT NULL,
  username TEXT,
  createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "Todo" (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- T00201: 找回密码流程验证码表（6位数字码，10分钟有效期，一次性使用）
CREATE TABLE IF NOT EXISTS "PasswordResetCode" (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL,
  code TEXT NOT NULL,
  expiresAt DATETIME NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 注意：User.email 字段的迁移不在此文件中执行。
-- T00103 已踩坑：本文件按 sql.init.mode=always 每次启动都重放，裸的
-- "ALTER TABLE ... ADD COLUMN" 在第二次启动时会因列已存在而抛错。
-- 因此 email 列沿用 T00103 的 PRAGMA table_info 探测 + 条件 ALTER 模式，
-- 由 UserRepository.ensureEmailColumn() 在启动时按条件执行。

-- 同理：User.avatarUrl 字段（T00301 头像上传）的迁移也不在此文件中执行，
-- 由 UserRepository.ensureAvatarColumn() 按同样的 PRAGMA 探测 + 条件 ALTER 模式
-- 在启动时执行。

