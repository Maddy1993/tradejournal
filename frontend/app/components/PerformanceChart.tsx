"use client";

import React from 'react';
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer
} from 'recharts';

interface PerformanceChartProps {
    trades: {
        tradeDate: string;
        realizedPl?: number;
    }[];
}

const PerformanceChart: React.FC<PerformanceChartProps> = ({ trades }) => {
    // Process trades to calculate cumulative P&L over time
    const data = React.useMemo(() => {
        // Sort trades by date
        const sortedTrades = [...trades].sort((a, b) =>
            new Date(a.tradeDate).getTime() - new Date(b.tradeDate).getTime()
        );

        // Aggregate P&L by date
        const dailyPnL = new Map<string, number>();

        sortedTrades.forEach(trade => {
            const date = new Date(trade.tradeDate).toLocaleDateString();
            const pnl = trade.realizedPl || 0;
            dailyPnL.set(date, (dailyPnL.get(date) || 0) + pnl);
        });

        // Calculate cumulative P&L
        let cumulative = 0;
        const chartData: { date: string; pnl: number; cumulative: number }[] = [];

        // Convert map to array and sort by date
        const sortedDates = Array.from(dailyPnL.keys()).sort((a, b) =>
            new Date(a).getTime() - new Date(b).getTime()
        );

        sortedDates.forEach(date => {
            const daily = dailyPnL.get(date) || 0;
            cumulative += daily;
            chartData.push({
                date,
                pnl: daily,
                cumulative
            });
        });

        return chartData;
    }, [trades]);

    if (data.length === 0) {
        return (
            <div className="flex items-center justify-center h-64 bg-gray-50 rounded-lg border-2 border-dashed border-gray-200 text-gray-400">
                No P&L data available to chart
            </div>
        );
    }

    return (
        <div className="h-80 w-full">
            <ResponsiveContainer width="100%" height="100%">
                <LineChart
                    data={data}
                    margin={{
                        top: 5,
                        right: 30,
                        left: 20,
                        bottom: 5,
                    }}
                >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" />
                    <YAxis />
                    <Tooltip
                        formatter={(value: number) => [`$${value.toFixed(2)}`, 'Cumulative P&L']}
                    />
                    <Legend />
                    <Line
                        type="monotone"
                        dataKey="cumulative"
                        name="Cumulative P&L"
                        stroke="#2563eb"
                        activeDot={{ r: 8 }}
                        strokeWidth={2}
                    />
                </LineChart>
            </ResponsiveContainer>
        </div>
    );
};

export default PerformanceChart;
