import type { Metadata } from "next";
import "./globals.css";
import { UserProvider } from "./context/UserContext";

export const metadata: Metadata = {
  title: "Trade Journal",
  description: "Track your trades, P&L, and dividends",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body
        className="antialiased"
        style={{ fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif' }}
      >
        <UserProvider>
          {children}
        </UserProvider>
      </body>
    </html>
  );
}
