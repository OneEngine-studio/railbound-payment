import React from "react";
import type { AppProps } from "next/app";
import Head from "next/head";
import { AuthProvider } from "../lib/auth";
import { Navbar } from "../components/Navbar";
import { Footer } from "../components/Footer";
import "../styles/globals.css";

export default function App({ Component, pageProps }: AppProps) {
  return (
    <AuthProvider>
      <Head>
        <title>Railbound Battle Royale — Payment & Asset Portal</title>
        <meta name="description" content="Official payment gateway and asset portal for Railbound Battle Royale." />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
      </Head>
      <div className="flex min-h-screen flex-col bg-[#09090b] text-zinc-100 selection:bg-amber-500/20 selection:text-amber-300">
        <Navbar />
        <main className="flex-1">
          <Component {...pageProps} />
        </main>
        <Footer />
      </div>
    </AuthProvider>
  );
}
