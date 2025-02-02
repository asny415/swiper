import * as alipay from "../app/src/main/assets/scripts/alipay.mjs";
import { execSync } from "child_process";
import { writeFileSync, unlinkSync } from "fs";
import { join } from "path";

const modules = { alipay };
let args = process.argv;
let targetDevice = "";
let saveHistory = false;
for (let i = 0; i < args.length; i++) {
  if (args[i] == "-s") {
    targetDevice = args[i + 1];
    args[i] = "";
    args[i + 1] = "";
  }
  if (args[i] == "--save-history") {
    saveHistory = true;
    args[i] = "";
  }
}
args = args.filter((arg) => arg);
const moduleName = args.slice(-1)[0];
const module = modules[moduleName];

if (!module) {
  console.error(`Module ${moduleName} does not exist.`);
  process.exit(1);
}

let deviceId = targetDevice;
if (!deviceId) {
  let deviceLines;
  try {
    const adbDevicesOutput = execSync("adb devices").toString();
    deviceLines = adbDevicesOutput
      .split("\n")
      .filter((line) => line.includes("\tdevice"));

    if (deviceLines.length !== 1) {
      console.error(
        "ADB is not running or there is not exactly one device online."
      );
      process.exit(1);
    }
  } catch (error) {
    console.error("Failed to run adb command:", error.message);
    process.exit(1);
  }
  deviceId = deviceLines[0].split("\t")[0];
}

console.log("启动运行", module.name, deviceId);

try {
  execSync(
    `adb -s ${deviceId} shell monkey -p ${module.pkg} -c android.intent.category.LAUNCHER 1`,
    {
      stdio: "ignore",
    }
  );
  console.log(`${module.pkg} launched successfully.`);
} catch (error) {
  console.error(`Failed to launch ${module.package}:`, error.message);
  process.exit(1);
}

process.on("SIGINT", async () => {
  console.log("Caught interrupt signal, cleaning up...");
  process.exit();
});

process.on("uncaughtException", async (err) => {
  console.error("Uncaught Exception:", err);
  execSync(`say "程序异常退出"`);
  process.exit(1);
});

await new Promise((r) => setTimeout(r, 5000));

const ctx = {};
while (true) {
  console.log(new Date(), "开始截屏...");
  const result = execSync(
    `adb -s ${deviceId} exec-out screencap -p | vision-ocr`
  ).toString();

  const screen = JSON.parse(result);
  console.log(new Date(), "截屏成功，节点数：", screen.results.length);
  console.log(JSON.stringify(screen));
  if (saveHistory) {
    const filePath = join(process.cwd(), `node.${new Date()}.json`);
    writeFileSync(filePath, JSON.stringify(screen, null, 2));
  }
  const { opts, ...others } = module.logic(
    { ...ctx, width: screen.imageWidth, height: screen.imageHeight },
    screen.results
  );
  Object.assign(ctx, others);
  for (const opt of opts) {
    await runOpt(deviceId, opt);
  }
  if (!opts.length) {
    await new Promise((r) => setTimeout(r, 5000));
  }
}

async function runOpt(deviceId, opration) {
  const { opt, reason, params } = opration;
  console.log(new Date(), reason, opt, params);
  if (opt === "click") {
    const { x, y } = params;
    execSync(`adb -s ${deviceId} shell input tap ${x} ${y}`, {
      stdio: "ignore",
    });
  } else if (opt === "swipe") {
    const { x1, y1, x2, y2, duration } = params;
    execSync(
      `adb -s ${deviceId} shell input swipe ${x1.toFixed(0)} ${y1.toFixed(
        0
      )} ${x2.toFixed(0)} ${y2.toFixed(0)} ${duration.toFixed(0)}`,
      {
        stdio: "ignore",
      }
    );
  } else if (opt === "back") {
    execSync(`adb -s ${deviceId} shell input keyevent 4`, {
      stdio: "ignore",
    });
  } else if (opt === "finish") {
    execSync(`say "任务完成"`);
    process.exit(0);
  } else if (opt === "sleep") {
    const { ms } = params;
    await new Promise((r) => setTimeout(r, ms));
  } else if (opt === "say") {
    execSync(`say "${reason}"`);
    const { ms } = params;
    await new Promise((r) => setTimeout(r, ms));
  } else {
    console.error(`Unknown opt: ${opt}`);
  }
}
