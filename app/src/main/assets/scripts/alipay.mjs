export const name = "支付宝视频助手";
export const description = "帮助你自动刷新支付宝视频";
export const version = "2025-01-25";
export function logic(ctx, nodes) {
  if (nodes.length === 0) {
    return {
      opts: [{ opt: "click", reason: "卡住了？", params: { x: 300, y: 600 } }],
    };
  }

  if (nodes[0].package !== "com.eg.android.AlipayGphone") {
    return { opts: [] };
  }

  const card = nodes.find((node) => node.text === "开心收下");
  if (card) {
    const [x, y] = JSON.parse(`${card.bounds.split("][")[0]}]`);
    return {
      opts: [
        {
          opt: "click",
          reason: "开心手下",
          params: {
            x: x + Math.random() * 50 + 50,
            y: y + Math.random() * 50 + 50,
          },
        },
      ],
    };
  }

  {
    const card = nodes.find((node) => node.text === "取消进入");
    if (card) {
      const [x, y] = JSON.parse(`${card.bounds.split("][")[0]}]`);
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
    const card = nodes.find((node) => node.desc === "关闭");
    if (card) {
      const [x, y] = JSON.parse(`${card.bounds.split("][")[0]}]`);
      return {
        opts: [
          {
            opt: "click",
            reason: "需要关闭",
            params: {
              x: x + Math.random() * 50 + 50,
              y: y + Math.random() * 50 + 50,
            },
          },
        ],
      };
    }
  }

  if (nodes[0].package == "com.eg.android.AlipayGphone") {
    const values = `[${nodes[0].bounds.split("][")[1]}`;
    const [width, height] = JSON.parse(values);
    const x1 = width / 2 + Math.random() * 30 - 15,
      y1 = (height * 3) / 4 + Math.random() * 100 - 50,
      x2 = width / 2 + Math.random() * 30 - 15,
      y2 = (height * 1) / 4 + Math.random() * 100 - 50;
    const duration = Math.random() * 100 - 50 + 600;
    if (ctx.lastSwipe && new Date().getTime() - ctx.lastSwipe < 5000) {
      return { opts: [{ opt: "sleep", reason: "等待", params: { ms: 500 } }] };
    }
    return {
      lastSwipe: new Date().getTime(),
      opts: [
        {
          opt: "swipe",
          reason: "正常滑动",
          params: { x1, y1, x2, y2, duration },
        },
      ],
    };
  }
}
