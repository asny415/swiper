export const name = "支付宝视频助手";
export const description = "帮助你自动刷新支付宝视频";
export const version = "2025-01-25";
export const pkg = "com.eg.android.AlipayGphone";
export function logic(ctx, nodes) {
  if (nodes.length === 0) {
    return {
      opts: [{ opt: "click", reason: "卡住了？", params: { x: 300, y: 600 } }],
    };
  }

  const card = nodes.find((node) => node.text.indexOf("开心收下") >= 0);
  if (card) {
    const { x, y } = card.boundingBox;
    return {
      opts: [
        {
          opt: "click",
          reason: "开心收下",
          params: {
            x: x + Math.random() * 50 + 50,
            y: y + Math.random() * 50 + 50,
          },
        },
      ],
    };
  }

  {
    const card = nodes.find((node) => node.text.indexOf("取消进入") >= 0);
    if (card) {
      const { x, y } = card.boundingBox;
      return {
        opts: [
          {
            opt: "click",
            reason: "取消进入",
            params: {
              x: x + Math.random() * 50 + 50,
              y: y + Math.random() * 50 + 50,
            },
          },
        ],
      };
    }
  }

  {
    const card = nodes.find((node) => `${node.text}`.endsWith("请进行验证"));
    if (card) {
      return {
        opts: [
          {
            opt: "say",
            reason: "等待人工验证",
            params: {
              ms: 1000,
            },
          },
        ],
      };
    }
  }
  {
    const card = nodes.find((node) => `${node.text}`.endsWith("明日可领"));
    if (card) {
      return {
        opts: [
          {
            opt: "finish",
            params: {},
          },
        ],
      };
    }
  }

  {
    const card = nodes.find(
      (node) => `${node.text}`.indexOf("支付宝祝你") >= 0
    );
    if (card) {
      return {
        opts: [
          {
            opt: "back",
            reason: "四时平安卡",
            params: {},
          },
        ],
      };
    }
  }

  {
    const targetcard = nodes.find(
      (node) => node.text.indexOf("直播") >= 0 || node.text.indexOf("微剧") >= 0
    );
    const entrycard = nodes.find(
      (node) => node.text === "视频" || node.text === "更新"
    );
    if (targetcard) {
      const { width, height } = ctx;
      const x1 = width / 2 + Math.random() * 30 - 15,
        y1 = (height * 3) / 4 + Math.random() * 100 - 50,
        x2 = width / 2 + Math.random() * 30 - 15,
        y2 = (height * 1) / 4 + Math.random() * 100 - 50;
      const duration = Math.random() * 100 - 50 + 600;
      const delay = Math.random() * 3000 + 3000;
      if (ctx.lastSwipe && new Date().getTime() - ctx.lastSwipe < delay) {
        return {
          opts: [{ opt: "sleep", reason: "等待", params: { ms: 500 } }],
        };
      }
      console.log("上次滑动间隔:", new Date().getTime() - ctx.lastSwipe);
      return {
        lastSwipe: new Date().getTime(),
        unknown: 0,
        opts: [
          {
            opt: "swipe",
            reason: "正常滑动",
            params: { x1, y1, x2, y2, duration },
          },
        ],
      };
    } else if (entrycard) {
      const { x, y } = entrycard.boundingBox;
      return {
        opts: [
          {
            opt: "click",
            reason: "点击视频",
            params: {
              x: x + Math.random() * 10 + 10,
              y: y + Math.random() * 10 + 10,
            },
          },
          {
            opt: "sleep",
            reason: "随机延迟",
            params: {
              ms: Math.random() * 1000 + 1000,
            },
          },
        ],
      };
    } else {
      const unknown = (ctx.unknown || 0) + 1;
      console.log("unknow but unknown is", unknown);
      if (unknown > 1) {
        return {
          opts: [
            {
              opt: "back",
              reason: "是不是进直播了，按一下返回吧",
            },
          ],
        };
      } else {
        return {
          unknown,
          opts: [{ opt: "sleep", reason: "等等再说", params: { ms: 500 } }],
        };
      }
    }
  }
}
