import { NextRequest, NextResponse } from "next/server";

const PUBLIC = ["/login", "/signup", "/forgot", "/reset", "/verify"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const publicRoute = PUBLIC.some((path) => pathname === path || pathname.startsWith(`${path}/`));
  const session = request.cookies.get("rm_session")?.value;

  if (!session && !publicRoute) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname);
    return NextResponse.redirect(login);
  }

  if (session && (pathname === "/login" || pathname === "/signup" || pathname.startsWith("/signup/"))) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|icon.svg|favicon.ico).*)"],
};
