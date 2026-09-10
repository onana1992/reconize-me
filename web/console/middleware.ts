import { NextRequest, NextResponse } from "next/server";
import { ENV_COOKIE, ENV_QUERY, resolveEnvironment } from "./lib/environment";

const PUBLIC = ["/login", "/signup", "/forgot", "/reset", "/verify"];
const PRODUCTS = ["/identity", "/biometrics", "/aml"];

function isProductPath(pathname: string): boolean {
  return PRODUCTS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const publicRoute = PUBLIC.some((path) => pathname === path || pathname.startsWith(`${path}/`));
  const session = request.cookies.get("rm_session")?.value;

  if (!session && !publicRoute) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname);
    return NextResponse.redirect(login);
  }

  if (session && isProductPath(pathname)) {
    const requested = request.nextUrl.searchParams.get(ENV_QUERY);
    const env = resolveEnvironment(requested ?? request.cookies.get(ENV_COOKIE)?.value);
    if (requested !== env) {
      const url = request.nextUrl.clone();
      url.searchParams.set(ENV_QUERY, env);
      return NextResponse.redirect(url);
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|icon.svg|favicon.ico).*)"],
};
