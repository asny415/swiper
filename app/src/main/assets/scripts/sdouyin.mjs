import { button, say, back, finish, swipeUp, b } from "./common/opts.mjs";
export const name = "抖音极速版刷助手";
export const description = "帮助你自动刷新抖音极速版领金币";
export const version = "2025-01-25";
export const pkg = "com.ss.android.ugc.aweme.lite";
export function logic(ctx, nodes) {
  const op = b({ button, say, back, finish, swipeUp }, ctx, nodes);
  return (
    op.swipeUp("热点", Math.random() * 3000 + 3000) ||
    op.swipeUp("首页", Math.random() * 3000 + 3000) ||
    op.swipeUp("推荐", Math.random() * 3000 + 3000)
  );
}
