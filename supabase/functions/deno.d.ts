// This file provides type definitions for the Deno namespace 
// to resolve "Cannot find name 'Deno'" errors in editors 
// when the Deno CLI is not installed locally.

declare namespace Deno {
  export interface Env {
    get(key: string): string | undefined;
    set(key: string, value: string): void;
    delete(key: string): void;
    toObject(): { [key: string]: string };
  }

  export const env: Env;

  export interface ServeOptions {
    port?: number;
    hostname?: string;
    onListen?: (params: { hostname: string; port: number }) => void;
    onError?: (error: unknown) => Response | Promise<Response>;
    onBeforeServe?: (params: { hostname: string; port: number }) => void;
  }

  export function serve(
    handler: (request: Request, info: any) => Response | Promise<Response>,
    options?: ServeOptions
  ): void;

  export function serve(
    options: ServeOptions & { handler: (request: Request, info: any) => Response | Promise<Response> }
  ): void;
}
