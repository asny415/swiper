export function swipeUp(ctx, nodes, text, delay) {
  const card = nodes.find((node) => node.text.indexOf(text) >= 0);
  if (card) {
    const { width, height } = ctx;
    const x1 = width / 2 + Math.random() * 30 - 15,
      y1 = (height * 3) / 4 + Math.random() * 100 - 50,
      x2 = width / 2 + Math.random() * 30 - 15,
      y2 = (height * 1) / 4 + Math.random() * 100 - 50;
    const duration = Math.random() * 100 - 50 + 600;
    return {
      opts: [
        {
          opt: "swipe",
          reason: text,
          params: {
            x1,
            y1,
            x2,
            y2,
            duration,
          },
        },
        {
          opt: "sleep",
          params: { ms: delay },
        },
      ],
    };
  }
}

export function back(ctx, nodes, text, reason) {
  {
    const card = nodes.find((node) => `${node.text}`.indexOf(text) >= 0);
    if (card) {
      return {
        opts: [
          {
            opt: "back",
            reason: reason || text,
          },
          { opt: "sleep", params: { ms: 3000 } },
        ],
      };
    }
  }
}

export function say(ctx, nodes, target, text) {
  const card = nodes.find((node) => `${node.text}`.indexOf(target) >= 0);
  if (card) {
    return {
      opts: [
        {
          opt: "say",
          reason: text,
        },
        {
          opt: "sleep",
          params: { ms: 1000 },
        },
      ],
    };
  }
}

export function button(ctx, nodes, text) {
  const card = nodes.find((node) => node.text.indexOf(text) >= 0);
  if (card) {
    const { x, y, width, height } = card.boundingBox;
    return {
      opts: [
        {
          opt: "click",
          reason: text,
          params: {
            x: x + (Math.random() * width) / 2 + width / 4,
            y: y + (Math.random() * height) / 2 + height / 4,
          },
        },
        { opt: "sleep", params: { ms: 2000 } },
      ],
    };
  }
}

export function finish(ctx, nodes, text) {
  const card = nodes.find((node) => `${node.text}`.indexOf(text) >= 0);
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

export function b(obj, c, n) {
  return Object.keys(obj).reduce(
    (r, i) => ({ ...r, [i]: obj[i].bind(null, c, n) }),
    {}
  );
}
