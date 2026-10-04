import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import net from "node:net";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const cwd = path.join(path.dirname(fileURLToPath(import.meta.url)), "..");
const port = Number(process.env.PORT || 3000);
const url = `http://localhost:${port}`;
const nextCli = createRequire(import.meta.url).resolve("next/dist/bin/next");

process.env.NEXT_TELEMETRY_DISABLED = "1";

console.log(`\nConsole: ${url}`);
console.log("Le navigateur s'ouvre dès que le port répond. La première compilation d’une page est plus longue ; les suivantes sont rapides.\n");

const child = spawn(process.execPath, [nextCli, "dev", "--turbopack", "--port", String(port)], {
  cwd,
  env: process.env,
  stdio: "inherit",
});

child.on("exit", (code, signal) => {
  if (signal) process.exit(1);
  process.exit(code ?? 0);
});

for (const signal of ["SIGINT", "SIGTERM", "SIGHUP"]) {
  process.on(signal, () => {
    if (!child.killed) child.kill(signal);
  });
}

waitForPort(port).then(() => openBrowser(url)).catch(() => {});

function waitForPort(listenPort) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + 120_000;
    const attempt = () => {
      const socket = net.connect({ host: "127.0.0.1", port: listenPort }, () => {
        socket.end();
        resolve();
      });
      socket.on("error", () => {
        socket.destroy();
        if (Date.now() > deadline) {
          reject(new Error(`Timed out waiting for ${url}`));
          return;
        }
        setTimeout(attempt, 250);
      });
    };
    attempt();
  });
}

function openBrowser(target) {
  if (process.platform === "win32") {
    spawn("cmd", ["/c", "start", "", target], { detached: true, stdio: "ignore" }).unref();
    return;
  }
  spawn(process.platform === "darwin" ? "open" : "xdg-open", [target], {
    detached: true,
    stdio: "ignore",
  }).unref();
}
