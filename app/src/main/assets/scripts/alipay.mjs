import { button, say, back, finish, swipeUp, b } from "./common/opts.mjs";
export const name = "支付宝视频助手";
export const description = "帮助你自动刷新支付宝视频";
export const version = "2025-01-25";
export const pkg = "com.eg.android.AlipayGphone";
export function logic(ctx, nodes) {
  const op = b({ button, say, back, finish, swipeUp }, ctx, nodes);
  return (
    op.finish("请尽快领取") ||
    op.button("开心收下") ||
    op.button("取消进入") ||
    op.say("请进行验证", "等待人工验证") ||
    op.back("支付宝祝你", "四时平安卡") ||
    op.back("立即添加", "添加直播广场") ||
    op.back("立即关注") ||
    op.back("更多直播") ||
    op.back("人看过", "直播中") ||
    op.finish("明日可领") ||
    op.swipeUp("发现", Math.random() * 3000 + 3000) ||
    op.swipeUp("直播", Math.random() * 3000 + 3000) ||
    op.swipeUp("关注", Math.random() * 3000 + 3000) ||
    op.swipeUp("收藏", Math.random() * 3000 + 3000) ||
    op.button("视频")
  );
}
