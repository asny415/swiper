import * as alipay from "../app/src/main/assets/scripts/alipay.mjs";
import { execSync } from "child_process";
import { writeFileSync } from "fs";
import { join } from "path";

const modules = { alipay };
const moduleName = process.argv[2];
const module = modules[moduleName];

if (!module) {
  console.error(`Module ${moduleName} does not exist.`);
  process.exit(1);
}

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

function parseNodesFromXml(xml) {
  const json = [];
  const regex = /<node[^>]*\/?>/g;
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

const deviceId = deviceLines[0].split("\t")[0];
console.log("启动运行", module.name, deviceId);
const ctx = {};
while (true) {
  try {
    execSync(`adb -s ${deviceId} shell rm /sdcard/window_dump.xml`, {
      stdio: "ignore",
    });
  } catch (error) {
    //这是正常的如果文件已经不存在
  }
  try {
    const result = execSync(
      `adb -s ${deviceId} shell ls /sdcard/window_dump.xml`
    ).toString();
    if (result.includes("/sdcard/window_dump.xml")) {
      console.log("/sdcard/window_dump.xml exists. Exiting.");
      process.exit(0);
    }
  } catch (error) {
    if (!error.message.includes("No such file or directory")) {
      console.error("Failed to check /sdcard/window_dump.xml:", error.message);
      process.exit(0);
    }
  }

  try {
    execSync(
      `adb -s ${deviceId} shell uiautomator dump /sdcard/window_dump.xml`,
      {
        stdio: "ignore",
      }
    );
  } catch (error) {
    console.error("Failed to dump window information:", error.message);
  }

  let xml;
  try {
    xml = execSync(
      `adb -s ${deviceId} shell cat /sdcard/window_dump.xml`
    ).toString();
    console.log("XML content retrieved successfully.");
  } catch (error) {
    console.error("Failed to retrieve XML content:", error.message);
  }

  const nodes = parseNodesFromXml(xml);
  const filePath = join(process.env.HOME, "Desktop", "node.json");
  writeFileSync(filePath, JSON.stringify(nodes, null, 2));
  console.log(`Nodes written to ${filePath}`);
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
  } else if (opt === "sleep") {
    const { ms } = params;
    await new Promise((r) => setTimeout(r, ms));
  } else {
    console.error(`Unknown opt: ${opt}`);
  }
}
