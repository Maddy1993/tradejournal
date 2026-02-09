"use client";

import React, { useEffect, useState, useMemo } from 'react';
import { useUser } from '../context/UserContext';
import PerformanceChart from './PerformanceChart';

// ==================== INTERFACES ====================
interface Holding {
    id: string;
    symbol: string;
    quantity: number;
    averageCost: number;
    currentPrice: number;
    marketValue: number;
    accountId?: string;
}

interface Dividend {
    id: string;
    symbol: string;
    amount: number;
    payDate: string;
    exDate: string | null;
    type?: string;
}

interface Trade {
    id: string;
    accountId: string;
    symbol: string;
    tradeDate: string;
    action: string;
    quantity: number;
    price: number;
    commission: number;
    fees: number;
    totalCost: number;
    realizedPl?: number;
    notes?: string;
}

interface TradeStats {
    totalTrades: number;
    uniqueSymbols: number;
    totalBought: number;
    totalSold: number;
    totalFees: number;
    netAmount: number;
}

interface PerformanceData {
    totalEquity?: number;
    totalAbsoluteReturn?: number;
    totalPercentReturn?: number;
    [key: string]: any;
}

interface DashboardData {
    totalEquity: number;
    unrealizedPL: number;
    totalPositions?: number;
    performance?: {
        totalGainLoss?: number;
    };
}

interface Position {
    symbol: string;
    status: 'open' | 'closed';
    quantity?: number;
    averageCost?: number;
    currentPrice?: number;
    marketValue?: number;
    unrealizedPl?: number;
    realizedPl: number;
    trades: Trade[];
    totalDividends: number;
    dividends: Dividend[];
    totalReturn: number;
}

// ==================== COMPONENT ====================
const Dashboard: React.FC = () => {
    // Get user from context
    const { email: userEmail, userId, connectedBrokers } = useUser();

    // State management
    const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
    const [holdings, setHoldings] = useState<Holding[]>([]);
    const [dividends, setDividends] = useState<Dividend[]>([]);
    const [trades, setTrades] = useState<Trade[]>([]);
    const [tradeStats, setTradeStats] = useState<TradeStats | null>(null);
    const [performance, setPerformance] = useState<PerformanceData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [expandedPositions, setExpandedPositions] = useState<Set<string>>(new Set());

    // ==================== DATA FETCHING ====================
    useEffect(() => {
        if (!userId || !userEmail) {
            setLoading(false);
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            setError(null);

            try {
                // Calculate date range for performance (last 30 days by default)
                const endDate = new Date().toISOString().split('T')[0];
                const startDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
                    .toISOString()
                    .split('T')[0];

                // Fetch all endpoints in parallel for better performance
                const [dashRes, holdingsRes, dividendsRes, perfRes, tradesRes, tradeStatsRes] = await Promise.allSettled([
                    fetch(`http://localhost:8080/api/dashboard?userId=${userId}`),
                    fetch(`http://localhost:8080/api/holdings?userId=${userEmail}`),
                    fetch(`http://localhost:8080/api/dividends?userId=${userId}`),
                    fetch(`http://localhost:8080/api/performance?userId=${userEmail}&startDate=${startDate}&endDate=${endDate}`),
                    fetch(`http://localhost:8080/api/trades?userId=${userEmail}&limit=500`),
                    fetch(`http://localhost:8080/api/trades/stats?userId=${userEmail}`),
                ]);

                // Process Dashboard data
                if (dashRes.status === 'fulfilled' && dashRes.value.ok) {
                    const data = await dashRes.value.json();
                    setDashboardData(data);
                } else {
                    console.warn('Dashboard API failed, will calculate from holdings');
                }

                // Process Holdings data
                if (holdingsRes.status === 'fulfilled' && holdingsRes.value.ok) {
                    const data = await holdingsRes.value.json();
                    const holdingsArray = Array.isArray(data) ? data : (data.holdings || []);
                    setHoldings(holdingsArray);
                } else {
                    console.warn('Holdings API failed:', holdingsRes);
                }

                // Process Dividends data
                if (dividendsRes.status === 'fulfilled' && dividendsRes.value.ok) {
                    const data = await dividendsRes.value.json();
                    const dividendsArray = Array.isArray(data) ? data : (data.dividends || []);
                    setDividends(dividendsArray);
                } else {
                    console.warn('Dividends API failed:', dividendsRes);
                }

                // Process Performance data
                if (perfRes.status === 'fulfilled' && perfRes.value.ok) {
                    const data = await perfRes.value.json();
                    setPerformance(data);
                } else {
                    console.warn('Performance API failed:', perfRes);
                }

                // Process Trades data
                if (tradesRes.status === 'fulfilled' && tradesRes.value.ok) {
                    const data = await tradesRes.value.json();
                    setTrades(Array.isArray(data) ? data : []);
                } else {
                    console.warn('Trades API failed:', tradesRes);
                }

                // Process Trade Stats data
                if (tradeStatsRes.status === 'fulfilled' && tradeStatsRes.value.ok) {
                    const data = await tradeStatsRes.value.json();
                    setTradeStats(data);
                } else {
                    console.warn('Trade Stats API failed:', tradeStatsRes);
                }

            } catch (err) {
                console.error('Error fetching dashboard data:', err);
                setError(err instanceof Error ? err.message : 'Failed to load dashboard data');
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [userId, userEmail]);

    // ==================== BUILD POSITIONS ====================
    const positions: Position[] = useMemo(() => {
        const posMap = new Map<string, Position>();

        // Seed from holdings (open positions)
        holdings.forEach(h => {
            const unrealizedPl = h.marketValue - (h.averageCost * h.quantity);
            posMap.set(h.symbol, {
                symbol: h.symbol,
                status: 'open',
                quantity: h.quantity,
                averageCost: h.averageCost,
                currentPrice: h.currentPrice,
                marketValue: h.marketValue,
                unrealizedPl,
                realizedPl: 0,
                trades: [],
                totalDividends: 0,
                dividends: [],
                totalReturn: unrealizedPl,
            });
        });

        // Add trade data
        trades.forEach(t => {
            let pos = posMap.get(t.symbol);
            if (!pos) {
                pos = {
                    symbol: t.symbol,
                    status: 'closed',
                    realizedPl: 0,
                    trades: [],
                    totalDividends: 0,
                    dividends: [],
                    totalReturn: 0,
                };
                posMap.set(t.symbol, pos);
            }
            pos.trades.push(t);
            if (t.realizedPl) {
                pos.realizedPl += t.realizedPl;
            }
        });

        // Add dividend data
        dividends.forEach(d => {
            let pos = posMap.get(d.symbol);
            if (!pos) {
                pos = {
                    symbol: d.symbol,
                    status: 'closed',
                    realizedPl: 0,
                    trades: [],
                    totalDividends: 0,
                    dividends: [],
                    totalReturn: 0,
                };
                posMap.set(d.symbol, pos);
            }
            pos.dividends.push(d);
            pos.totalDividends += d.amount;
        });

        // Recalculate total return
        posMap.forEach(pos => {
            pos.totalReturn = (pos.unrealizedPl || 0) + pos.realizedPl + pos.totalDividends;
        });

        // Sort: open first by market value desc, then closed by most recent trade
        const result = Array.from(posMap.values());
        result.sort((a, b) => {
            if (a.status === 'open' && b.status !== 'open') return -1;
            if (a.status !== 'open' && b.status === 'open') return 1;
            if (a.status === 'open' && b.status === 'open') {
                return (b.marketValue || 0) - (a.marketValue || 0);
            }
            // Both closed — sort by most recent trade
            const aLatest = a.trades.length > 0 ? new Date(a.trades[a.trades.length - 1].tradeDate).getTime() : 0;
            const bLatest = b.trades.length > 0 ? new Date(b.trades[b.trades.length - 1].tradeDate).getTime() : 0;
            return bLatest - aLatest;
        });

        return result;
    }, [holdings, trades, dividends]);

    // ==================== HELPER FUNCTIONS ====================
    const formatCurrency = (value: number | undefined | null): string => {
        if (value === undefined || value === null) return '$0.00';
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(value);
    };

    const formatPercentage = (value: number, total: number): string => {
        if (total === 0) return '0.00%';
        const percentage = (value / total) * 100;
        return `${percentage >= 0 ? '+' : ''}${percentage.toFixed(2)}%`;
    };

    const togglePosition = (symbol: string) => {
        setExpandedPositions(prev => {
            const next = new Set(prev);
            if (next.has(symbol)) next.delete(symbol);
            else next.add(symbol);
            return next;
        });
    };

    // Calculate derived values
    const totalEquity = dashboardData?.totalEquity || holdings.reduce((sum, h) => sum + h.marketValue, 0);
    const unrealizedPL = dashboardData?.unrealizedPL || holdings.reduce((sum, h) => {
        return sum + (h.marketValue - (h.averageCost * h.quantity));
    }, 0);
    const totalRealizedPL = positions.reduce((sum, p) => sum + p.realizedPl, 0);
    const totalDividends = dividends.reduce((sum, div) => sum + div.amount, 0);
    const totalReturnAll = unrealizedPL + totalRealizedPL + totalDividends;

    // ==================== LOADING STATE ====================
    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-gray-50">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-16 w-16 border-b-4 border-blue-600 mb-4"></div>
                    <h3 className="text-xl font-semibold text-gray-700 mb-2">Loading Portfolio</h3>
                    <p className="text-gray-500">Fetching your holdings, performance, and dividends...</p>
                </div>
            </div>
        );
    }

    // ==================== ERROR STATE ====================
    if (error) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-gray-50">
                <div className="bg-white border-l-4 border-red-500 rounded-lg shadow-lg p-6 max-w-md">
                    <div className="flex items-start">
                        <div className="flex-shrink-0">
                            <svg className="h-6 w-6 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                        </div>
                        <div className="ml-3 flex-1">
                            <h3 className="text-lg font-semibold text-red-800 mb-2">Error Loading Dashboard</h3>
                            <p className="text-sm text-red-700 mb-4">{error}</p>
                            <button
                                onClick={() => window.location.reload()}
                                className="bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 transition-colors duration-200 text-sm font-medium"
                            >
                                Retry
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    // ==================== MAIN DASHBOARD RENDER ====================
    return (
        <div className="min-h-screen bg-gray-50 p-6 space-y-6">
            {/* ==================== SUMMARY CARDS ==================== */}
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-5">
                {/* Total Equity Card */}
                <div className="bg-gradient-to-br from-blue-500 to-blue-700 rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200">
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Total Equity</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(totalEquity)}</p>
                    <p className="text-xs opacity-75">{holdings.length} positions</p>
                </div>

                {/* Unrealized P&L Card */}
                <div className={`rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200 ${unrealizedPL >= 0
                    ? 'bg-gradient-to-br from-green-500 to-green-700'
                    : 'bg-gradient-to-br from-red-500 to-red-700'
                    }`}>
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Unrealized P&L</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={unrealizedPL >= 0 ? "M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" : "M13 17h8m0 0V9m0 8l-8-8-4 4-6-6"} />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(unrealizedPL)}</p>
                    <p className="text-xs opacity-75">
                        {unrealizedPL >= 0 ? '↑' : '↓'} {formatPercentage(unrealizedPL, totalEquity - unrealizedPL)}
                    </p>
                </div>

                {/* Realized P&L Card */}
                <div className={`rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200 ${totalRealizedPL >= 0
                    ? 'bg-gradient-to-br from-emerald-500 to-emerald-700'
                    : 'bg-gradient-to-br from-rose-500 to-rose-700'
                    }`}>
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Realized P&L</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(totalRealizedPL)}</p>
                    <p className="text-xs opacity-75">
                        From {tradeStats?.totalTrades || 0} trades
                    </p>
                </div>

                {/* Total Return Card */}
                <div className="bg-gradient-to-br from-purple-500 to-purple-700 rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200">
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Total Return</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(totalReturnAll)}</p>
                    <p className="text-xs opacity-75">
                        Unrealized + Realized + Dividends
                    </p>
                </div>

                {/* Dividends Card */}
                <div className="bg-gradient-to-br from-amber-500 to-amber-700 rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200">
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Total Dividends</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(totalDividends)}</p>
                    <p className="text-xs opacity-75">{dividends.length} payments tracked</p>
                </div>
            </div>

            {/* ==================== CUMULATIVE P&L CHART ==================== */}
            {trades.length > 0 && (
                <div className="bg-white rounded-xl shadow-lg p-6">
                    <h2 className="text-xl font-semibold text-gray-800 mb-4">Cumulative P&L</h2>
                    <div className="bg-white rounded-lg p-4 border border-gray-200">
                        <PerformanceChart trades={trades} />
                    </div>
                </div>
            )}

            {/* ==================== UNIFIED POSITIONS TABLE ==================== */}
            <div className="bg-white rounded-xl shadow-lg overflow-hidden">
                <div className="px-6 py-4 bg-gray-50 border-b border-gray-200">
                    <div className="flex items-center justify-between">
                        <h2 className="text-xl font-semibold text-gray-800">Positions</h2>
                        <span className="text-sm text-gray-500">{positions.length} symbols</span>
                    </div>
                </div>
                <div className="overflow-x-auto">
                    {positions.length > 0 ? (
                        <table className="w-full">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-8"></th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Symbol</th>
                                    <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Qty</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Avg Cost</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Current</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Mkt Value</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Unrealized</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Realized</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Dividends</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Total Return</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {positions.map((pos) => (
                                    <React.Fragment key={pos.symbol}>
                                        <tr
                                            className="hover:bg-gray-50 cursor-pointer transition-colors duration-150"
                                            onClick={() => togglePosition(pos.symbol)}
                                        >
                                            <td className="px-4 py-4 text-gray-500">
                                                <svg className={`w-4 h-4 transition-transform ${expandedPositions.has(pos.symbol) ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                                </svg>
                                            </td>
                                            <td className="px-4 py-4 whitespace-nowrap">
                                                <div className="text-sm font-bold text-gray-900">{pos.symbol}</div>
                                            </td>
                                            <td className="px-4 py-4 whitespace-nowrap text-center">
                                                <span className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${pos.status === 'open' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'}`}>
                                                    {pos.status === 'open' ? 'Open' : 'Closed'}
                                                </span>
                                            </td>
                                            <td className="px-4 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                                {pos.quantity != null ? pos.quantity.toFixed(4) : '-'}
                                            </td>
                                            <td className="px-4 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                                {pos.averageCost != null ? formatCurrency(pos.averageCost) : '-'}
                                            </td>
                                            <td className="px-4 py-4 whitespace-nowrap text-sm text-right font-medium text-gray-900">
                                                {pos.currentPrice != null ? formatCurrency(pos.currentPrice) : '-'}
                                            </td>
                                            <td className="px-4 py-4 whitespace-nowrap text-sm text-right font-semibold text-gray-900">
                                                {pos.marketValue != null ? formatCurrency(pos.marketValue) : '-'}
                                            </td>
                                            <td className={`px-4 py-4 whitespace-nowrap text-sm text-right font-semibold ${(pos.unrealizedPl || 0) >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                                {pos.unrealizedPl != null ? formatCurrency(pos.unrealizedPl) : '-'}
                                            </td>
                                            <td className={`px-4 py-4 whitespace-nowrap text-sm text-right font-semibold ${pos.realizedPl >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                                {formatCurrency(pos.realizedPl)}
                                            </td>
                                            <td className="px-4 py-4 whitespace-nowrap text-sm text-right text-green-600 font-semibold">
                                                {pos.totalDividends > 0 ? formatCurrency(pos.totalDividends) : '-'}
                                            </td>
                                            <td className={`px-4 py-4 whitespace-nowrap text-sm text-right font-bold ${pos.totalReturn >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                                {formatCurrency(pos.totalReturn)}
                                            </td>
                                        </tr>
                                        {/* Expanded detail */}
                                        {expandedPositions.has(pos.symbol) && (
                                            <tr>
                                                <td colSpan={11} className="px-4 py-0">
                                                    <div className="bg-gray-50 rounded-lg p-4 my-2 space-y-4">
                                                        {/* Trades Table */}
                                                        {pos.trades.length > 0 && (
                                                            <div>
                                                                <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Trade History</h4>
                                                                <table className="w-full text-xs">
                                                                    <thead>
                                                                        <tr className="text-gray-500 border-b border-gray-200">
                                                                            <th className="py-2 text-left font-medium">Date</th>
                                                                            <th className="py-2 text-center font-medium">Action</th>
                                                                            <th className="py-2 text-right font-medium">Qty</th>
                                                                            <th className="py-2 text-right font-medium">Price</th>
                                                                            <th className="py-2 text-right font-medium">Total Cost</th>
                                                                            <th className="py-2 text-right font-medium">Realized P&L</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {pos.trades
                                                                            .sort((a, b) => new Date(a.tradeDate).getTime() - new Date(b.tradeDate).getTime())
                                                                            .map(t => (
                                                                            <tr key={t.id} className="border-b border-gray-100">
                                                                                <td className="py-2 text-gray-600">
                                                                                    {new Date(t.tradeDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                                                                                </td>
                                                                                <td className="py-2 text-center">
                                                                                    <span className={`px-2 py-0.5 rounded-full font-semibold ${t.action.includes('BUY') ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                                                                                        {t.action}
                                                                                    </span>
                                                                                </td>
                                                                                <td className="py-2 text-right text-gray-700">{t.quantity.toFixed(4)}</td>
                                                                                <td className="py-2 text-right text-gray-700">{formatCurrency(t.price)}</td>
                                                                                <td className="py-2 text-right text-gray-900 font-medium">{formatCurrency(t.totalCost)}</td>
                                                                                <td className={`py-2 text-right font-semibold ${(t.realizedPl || 0) > 0 ? 'text-green-600' : (t.realizedPl || 0) < 0 ? 'text-red-600' : 'text-gray-400'}`}>
                                                                                    {t.realizedPl != null ? formatCurrency(t.realizedPl) : '-'}
                                                                                </td>
                                                                            </tr>
                                                                        ))}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        )}
                                                        {/* Dividends Table */}
                                                        {pos.dividends.length > 0 && (
                                                            <div>
                                                                <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Dividends</h4>
                                                                <table className="w-full text-xs">
                                                                    <thead>
                                                                        <tr className="text-gray-500 border-b border-gray-200">
                                                                            <th className="py-2 text-left font-medium">Pay Date</th>
                                                                            <th className="py-2 text-left font-medium">Ex-Date</th>
                                                                            <th className="py-2 text-right font-medium">Amount</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {pos.dividends.map(d => (
                                                                            <tr key={d.id} className="border-b border-gray-100">
                                                                                <td className="py-2 text-gray-600">
                                                                                    {new Date(d.payDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                                                                                </td>
                                                                                <td className="py-2 text-gray-600">
                                                                                    {d.exDate ? new Date(d.exDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '-'}
                                                                                </td>
                                                                                <td className="py-2 text-right text-green-600 font-semibold">{formatCurrency(d.amount)}</td>
                                                                            </tr>
                                                                        ))}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        )}
                                                        {pos.trades.length === 0 && pos.dividends.length === 0 && (
                                                            <p className="text-xs text-gray-400">No trade or dividend history available</p>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        )}
                                    </React.Fragment>
                                ))}
                            </tbody>
                        </table>
                    ) : (
                        <div className="px-6 py-16 text-center">
                            <svg className="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">No Positions Found</h3>
                            {connectedBrokers.length > 0 ? (
                                <p className="text-gray-500 mb-4">
                                    Your account has no active positions. Go to Trades to sync, or add positions through your broker.
                                </p>
                            ) : (
                                <>
                                    <p className="text-gray-500 mb-4">Connect your brokerage account to see your portfolio</p>
                                    <a
                                        href="/settings"
                                        className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-blue-600 hover:bg-blue-700"
                                    >
                                        Connect Broker
                                    </a>
                                </>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default Dashboard;
