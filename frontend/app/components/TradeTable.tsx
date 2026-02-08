"use client";

import React, { useEffect, useState } from 'react';

interface Holding {
  id: string;
  accountId: string;
  symbol: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  marketValue: number;
  lastUpdated: string;
}

const TradeTable: React.FC = () => {
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Hardcoded user ID for now
  const userId = "123e4567-e89b-12d3-a456-426614174000";

  useEffect(() => {
    const fetchHoldings = async () => {
      try {
        const response = await fetch(`http://localhost:8080/api/holdings?userId=${userId}`);
        if (!response.ok) {
          throw new Error('Failed to fetch holdings');
        }
        const data = await response.json();
        setHoldings(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setLoading(false);
      }
    };

    fetchHoldings();
  }, [userId]);

  if (loading) return <div className="p-4 text-center">Loading holdings...</div>;
  if (error) return <div className="p-4 text-center text-red-500">Error: {error}</div>;

  return (
    <div className="overflow-x-auto shadow-md sm:rounded-lg">
      <table className="w-full text-sm text-left text-gray-400">
        <thead className="text-xs uppercase bg-gray-700 text-gray-400">
          <tr>
            <th scope="col" className="px-6 py-3">Symbol</th>
            <th scope="col" className="px-6 py-3">Quantity</th>
            <th scope="col" className="px-6 py-3">Avg Cost</th>
            <th scope="col" className="px-6 py-3">Current Price</th>
            <th scope="col" className="px-6 py-3">Market Value</th>
            <th scope="col" className="px-6 py-3">Last Updated</th>
          </tr>
        </thead>
        <tbody>
          {holdings.map((holding) => (
            <tr key={holding.id} className="border-b bg-gray-800 border-gray-700 hover:bg-gray-600">
              <td className="px-6 py-4 font-medium text-white whitespace-nowrap">
                {holding.symbol}
              </td>
              <td className="px-6 py-4">
                {holding.quantity}
              </td>
              <td className="px-6 py-4">
                ${holding.averageCost.toFixed(2)}
              </td>
              <td className="px-6 py-4">
                ${holding.currentPrice.toFixed(2)}
              </td>
              <td className="px-6 py-4">
                ${holding.marketValue.toFixed(2)}
              </td>
              <td className="px-6 py-4">
                {new Date(holding.lastUpdated).toLocaleString()}
              </td>
            </tr>
          ))}
          {holdings.length === 0 && (
            <tr>
              <td colSpan={6} className="px-6 py-4 text-center">No holdings found. Sync your broker first.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
};

export default TradeTable;
