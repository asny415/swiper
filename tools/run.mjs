import * as alipay from "../app/src/main/assets/scripts/alipay.mjs";
import { execSync } from "child_process";
import { writeFileSync } from "fs";
import { join } from "path";
import { spawn } from "child_process";

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

function parseNodesFromXml(xml) {
  const json = [];
  const regex = /<android.widget.[^ ]*[^>]*\/?>/g;
  let match;
  while ((match = regex.exec(xml)) !== null) {
    const nodeString = match[0];
    const node = {};
    const attributes = nodeString.match(/(\w+)="([^"]*)"/g);
    attributes.forEach((attr) => {
      const [key, value] = attr.split("=");
      node[key] = value.replace(/"/g, "");
    });
    json.push(node);
  }
  return json;
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

const localPort = 8000 + Math.floor(Math.random() * 1000);
execSync(`adb -s ${deviceId} forward tcp:${localPort} tcp:6790`);

execSync(
  `adb -s ${deviceId} shell am force-stop io.appium.uiautomator2.server`
);
execSync(
  `adb -s ${deviceId} shell am force-stop io.appium.uiautomator2.server.test`
);
execSync(
  `adb -s ${deviceId} shell 'dumpsys deviceidle whitelist +io.appium.settings ; dumpsys deviceidle whitelist +io.appium.uiautomator2.server ; dumpsys deviceidle whitelist +io.appium.uiautomator2.server.test ;'`
);

async function clear() {
  execSync(`adb -s ${deviceId} forward --remove tcp:${localPort}`);
  execSync(
    `adb -s ${deviceId} shell am force-stop io.appium.uiautomator2.server`
  );
  execSync(
    `adb -s ${deviceId} shell am force-stop io.appium.uiautomator2.server.test`
  );
}

process.on("SIGINT", async () => {
  console.log("Caught interrupt signal, cleaning up...");
  await clear();
  process.exit();
});

process.on("uncaughtException", async (err) => {
  console.error("Uncaught Exception:", err);
  await clear();
  execSync(`say "程序异常退出"`);
  process.exit(1);
});

const proxy = spawn("adb", [
  "-s",
  deviceId,
  "shell",
  "am",
  "instrument",
  "-w",
  "-e",
  "disableAnalytics",
  "true",
  "io.appium.uiautomator2.server.test/androidx.test.runner.AndroidJUnitRunner",
]);
proxy.stdout.on("data", (data) => {
  console.log(`proxy stdout: ${data}`);
});
proxy.stderr.on("data", (data) => {
  console.error(`proxy stderr: ${data}`);
});
proxy.on("close", (code) => {
  console.log(`proxy exited with code ${code}`);
});

await new Promise((r) => setTimeout(r, 5000));

const res = await fetch(`http://127.0.0.1:${localPort}/session`, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    capabilities: {
      firstMatch: [
        {
          platformName: "Android",
          automationName: "UiAutomator2",
        },
      ],
    },
  }),
});
const json = await res.json();
console.log(json);
const sessionId = json.value.sessionId;
if (!sessionId) {
  console.log("创建session失败");
  process.exit(0);
}

const ctx = {};
while (true) {
  console.log(new Date(), "开始截屏...");
  const pageres = await fetch(
    `http://127.0.0.1:${localPort}/session/${sessionId}/source`
  );
  const pagejson = await pageres.json();
  const xml = pagejson.value;
  const nodes = parseNodesFromXml(xml);
  console.log(new Date(), "截屏成功，节点数：", nodes.length);
  if (saveHistory) {
    const filePath = join(process.cwd(), `node.${new Date()}.json`);
    writeFileSync(filePath, JSON.stringify(nodes, null, 2));
  }
  const { opts, ...others } = module.logic(ctx, nodes);
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
    await clear();
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
