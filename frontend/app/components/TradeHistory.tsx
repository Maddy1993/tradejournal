"use client";

import React, { useEffect, useState, useMemo } from 'react';
import { useUser } from '../context/UserContext';

// ==================== INTERFACES ====================
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
    strategyGroupId?: string;
    notes?: string;
}
import PerformanceChart from './PerformanceChart';

interface TradeStats {
    totalTrades: number;
    uniqueSymbols: number;
    totalBought: number;
    totalSold: number;
    totalFees: number;
    netAmount: number;
}

interface TradePair {
    entryTrade: Trade;
    exitTrade: Trade | null;
    quantity: number;
    entryPrice: number;
    exitPrice: number | null;
    realizedPl: number | null;
    status: 'closed' | 'open';
}

interface SymbolGroup {
    symbol: string;
    pairs: TradePair[];
    totalQuantity: number;
    netPl: number;
    openQuantity: number;
}

// ==================== GROUPING UTILITY ====================
function groupTradesIntoPairs(trades: Trade[]): SymbolGroup[] {
    const bySymbol = new Map<string, Trade[]>();
    trades.forEach(t => {
        const list = bySymbol.get(t.symbol) || [];
        list.push(t);
        bySymbol.set(t.symbol, list);
    });

    const groups: SymbolGroup[] = [];

    bySymbol.forEach((symbolTrades, symbol) => {
        // Sort chronologically
        const sorted = [...symbolTrades].sort(
            (a, b) => new Date(a.tradeDate).getTime() - new Date(b.tradeDate).getTime()
        );

        const buyQueue: { trade: Trade; remainingQty: number }[] = [];
        const pairs: TradePair[] = [];

        sorted.forEach(trade => {
            const isBuy = trade.action.includes('BUY');
            const isSell = trade.action.includes('SELL');

            if (isBuy) {
                buyQueue.push({ trade, remainingQty: trade.quantity });
            } else if (isSell) {
                let sellQtyLeft = trade.quantity;
                while (sellQtyLeft > 0 && buyQueue.length > 0) {
                    const front = buyQueue[0];
                    const matchQty = Math.min(sellQtyLeft, front.remainingQty);
                    const pl = matchQty * (trade.price - front.trade.price);

                    pairs.push({
                        entryTrade: front.trade,
                        exitTrade: trade,
                        quantity: matchQty,
                        entryPrice: front.trade.price,
                        exitPrice: trade.price,
                        realizedPl: pl,
                        status: 'closed',
                    });

                    front.remainingQty -= matchQty;
                    sellQtyLeft -= matchQty;

                    if (front.remainingQty <= 0) {
                        buyQueue.shift();
                    }
                }
            }
        });

        // Remaining open buys
        buyQueue.forEach(entry => {
            if (entry.remainingQty > 0) {
                pairs.push({
                    entryTrade: entry.trade,
                    exitTrade: null,
                    quantity: entry.remainingQty,
                    entryPrice: entry.trade.price,
                    exitPrice: null,
                    realizedPl: null,
                    status: 'open',
                });
            }
        });

        const netPl = pairs.reduce((sum, p) => sum + (p.realizedPl || 0), 0);
        const totalQuantity = pairs.reduce((sum, p) => sum + p.quantity, 0);
        const openQuantity = pairs.filter(p => p.status === 'open').reduce((sum, p) => sum + p.quantity, 0);

        groups.push({ symbol, pairs, totalQuantity, netPl, openQuantity });
    });

    // Sort: symbols with open positions first, then by net P&L
    groups.sort((a, b) => {
        if (a.openQuantity > 0 && b.openQuantity <= 0) return -1;
        if (a.openQuantity <= 0 && b.openQuantity > 0) return 1;
        return b.netPl - a.netPl;
    });

    return groups;
}

// ==================== COMPONENT ====================
const TradeHistory: React.FC = () => {
    // Get user email from context
    const { email } = useUser();
    const userId = email || ''; // Use email as userId

    // State management
    const [trades, setTrades] = useState<Trade[]>([]);
    const [stats, setStats] = useState<TradeStats | null>(null);
    const [loading, setLoading] = useState(true);
    const [syncing, setSyncing] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
    const [viewMode, setViewMode] = useState<'flat' | 'grouped'>('flat');
    const [expandedSymbols, setExpandedSymbols] = useState<Set<string>>(new Set());

    // Filter state
    const [symbolFilter, setSymbolFilter] = useState('');
    const [actionFilter, setActionFilter] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [limit, setLimit] = useState(100);

    // Grouped data
    const symbolGroups = useMemo(() => groupTradesIntoPairs(trades), [trades]);

    const toggleExpanded = (symbol: string) => {
        setExpandedSymbols(prev => {
            const next = new Set(prev);
            if (next.has(symbol)) next.delete(symbol);
            else next.add(symbol);
            return next;
        });
    };

    // ==================== DATA FETCHING ====================
    useEffect(() => {
        // Don't fetch if user hasn't loaded yet
        if (!userId) return;

        fetchTrades();
        fetchStats();
    }, [userId, symbolFilter, actionFilter, startDate, endDate, limit]);

    const fetchTrades = async () => {
        setLoading(true);
        setError(null);

        try {
            // Build query parameters
            const params = new URLSearchParams({ userId });
            if (symbolFilter) params.append('symbol', symbolFilter);
            if (actionFilter) params.append('action', actionFilter);
            if (startDate) params.append('startDate', new Date(startDate).toISOString());
            if (endDate) params.append('endDate', new Date(endDate).toISOString());
            if (limit) params.append('limit', limit.toString());

            const response = await fetch(`http://localhost:8080/api/trades?${params}`);

            if (!response.ok) {
                throw new Error('Failed to fetch trades');
            }

            const data = await response.json();
            setTrades(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('Error fetching trades:', err);
            setError(err instanceof Error ? err.message : 'Failed to load trades');
        } finally {
            setLoading(false);
        }
    };

    const fetchStats = async () => {
        try {
            const response = await fetch(`http://localhost:8080/api/trades/stats?userId=${userId}`);
            if (response.ok) {
                const data = await response.json();
                setStats(data);
            }
        } catch (err) {
            console.warn('Failed to fetch trade stats:', err);
        }
    };

    const handleSyncTrades = async () => {
        setSyncing(true);
        setToast(null);
        try {
            const response = await fetch('http://localhost:8080/api/trades/sync', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: userId, // Use email from context
                    startDate: '2024-01-01',
                    endDate: new Date().toISOString().split('T')[0]
                })
            });

            if (response.ok) {
                const result = await response.json();
                setToast({
                    message: `Successfully synced ${result.trades_synced || 0} trades!`,
                    type: 'success'
                });
                fetchTrades();
                fetchStats();
            } else {
                const error = await response.json();
                setToast({
                    message: `Failed to sync: ${error.message || 'Unknown error'}`,
                    type: 'error'
                });
            }
        } catch (err) {
            setToast({
                message: `Error syncing trades: ${err}`,
                type: 'error'
            });
        } finally {
            setSyncing(false);
            // Auto-hide toast after 5 seconds
            setTimeout(() => setToast(null), 5000);
        }
    };

    const clearFilters = () => {
        setSymbolFilter('');
        setActionFilter('');
        setStartDate('');
        setEndDate('');
        setLimit(100);
    };

    // ==================== HELPER FUNCTIONS ====================
    const formatCurrency = (value: number | null | undefined): string => {
        if (value === null || value === undefined) return '$0.00';
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(value);
    };

    const formatDate = (dateString: string): string => {
        return new Date(dateString).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const formatDateShort = (dateString: string): string => {
        return new Date(dateString).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
        });
    };

    const getActionColor = (action: string): string => {
        if (action.includes('BUY')) return 'text-green-600 bg-green-50';
        if (action.includes('SELL')) return 'text-red-600 bg-red-50';
        return 'text-gray-600 bg-gray-50';
    };

    // ==================== LOADING STATE ====================
    if (loading && trades.length === 0) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-gray-50">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-16 w-16 border-b-4 border-blue-600 mb-4"></div>
                    <h3 className="text-xl font-semibold text-gray-700 mb-2">Loading Trades</h3>
                    <p className="text-gray-500">Fetching your trade history...</p>
                </div>
            </div>
        );
    }

    // ==================== ERROR STATE ====================
    if (error && trades.length === 0) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-gray-50">
                <div className="bg-white border-l-4 border-red-500 rounded-lg shadow-lg p-6 max-w-md">
                    <h3 className="text-lg font-semibold text-red-800 mb-2">Error Loading Trades</h3>
                    <p className="text-sm text-red-700 mb-4">{error}</p>
                    <button
                        onClick={() => window.location.reload()}
                        className="bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700"
                    >
                        Retry
                    </button>
                </div>
            </div>
        );
    }

    // ==================== MAIN RENDER ====================
    return (
        <div className="min-h-screen bg-gray-50 p-6 space-y-6">
            {/* Toast Notification */}
            {toast && (
                <div className={`fixed top-4 right-4 z-50 px-6 py-4 rounded-lg shadow-lg flex items-center gap-3 ${toast.type === 'success' ? 'bg-green-500' : 'bg-red-500'
                    } text-white animate-slide-in`}>
                    {toast.type === 'success' ? (
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                        </svg>
                    ) : (
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    )}
                    <span className="font-medium">{toast.message}</span>
                    <button onClick={() => setToast(null)} className="ml-4">
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>
            )}
            {/* ==================== HEADER & STATS ==================== */}
            <div className="bg-white rounded-xl shadow-lg p-6">
                <div className="flex items-center justify-between mb-6">
                    <h1 className="text-3xl font-bold text-gray-800">Trade History</h1>
                    <button
                        onClick={handleSyncTrades}
                        disabled={syncing}
                        className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                    >
                        <svg className={`w-5 h-5 ${syncing ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                        </svg>
                        {syncing ? 'Syncing...' : 'Sync Trades'}
                    </button>
                </div>

                <div className="mb-8">
                    <h2 className="text-xl font-semibold text-gray-800 mb-4">Performance</h2>
                    <div className="bg-white rounded-lg p-4 border border-gray-200">
                        <PerformanceChart trades={trades} />
                    </div>
                </div>

                {stats && (
                    <div className="grid gap-4 md:grid-cols-5">
                        <div className="bg-blue-50 rounded-lg p-4">
                            <p className="text-sm text-blue-600 font-medium">Total Trades</p>
                            <p className="text-2xl font-bold text-blue-900">{stats.totalTrades}</p>
                        </div>
                        <div className="bg-purple-50 rounded-lg p-4">
                            <p className="text-sm text-purple-600 font-medium">Unique Symbols</p>
                            <p className="text-2xl font-bold text-purple-900">{stats.uniqueSymbols}</p>
                        </div>
                        <div className="bg-green-50 rounded-lg p-4">
                            <p className="text-sm text-green-600 font-medium">Total Bought</p>
                            <p className="text-2xl font-bold text-green-900">{formatCurrency(stats.totalBought)}</p>
                        </div>
                        <div className="bg-red-50 rounded-lg p-4">
                            <p className="text-sm text-red-600 font-medium">Total Sold</p>
                            <p className="text-2xl font-bold text-red-900">{formatCurrency(stats.totalSold)}</p>
                        </div>
                        <div className="bg-amber-50 rounded-lg p-4">
                            <p className="text-sm text-amber-600 font-medium">Total Fees</p>
                            <p className="text-2xl font-bold text-amber-900">{formatCurrency(stats.totalFees)}</p>
                        </div>
                    </div>
                )}
            </div>

            {/* ==================== FILTERS ==================== */}
            <div className="bg-white rounded-xl shadow-lg p-6">
                <h2 className="text-xl font-semibold text-gray-800 mb-4">Filters</h2>
                <div className="grid gap-4 md:grid-cols-5">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Symbol</label>
                        <input
                            type="text"
                            value={symbolFilter}
                            onChange={(e) => setSymbolFilter(e.target.value.toUpperCase())}
                            placeholder="e.g., AAPL"
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Action</label>
                        <select
                            value={actionFilter}
                            onChange={(e) => setActionFilter(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        >
                            <option value="">All Actions</option>
                            <option value="BUY">BUY</option>
                            <option value="SELL">SELL</option>
                            <option value="BUY_TO_OPEN">BUY TO OPEN</option>
                            <option value="SELL_TO_CLOSE">SELL TO CLOSE</option>
                            <option value="SELL_TO_OPEN">SELL TO OPEN</option>
                            <option value="BUY_TO_CLOSE">BUY TO CLOSE</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Start Date</label>
                        <input
                            type="date"
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">End Date</label>
                        <input
                            type="date"
                            value={endDate}
                            onChange={(e) => setEndDate(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Limit</label>
                        <select
                            value={limit}
                            onChange={(e) => setLimit(parseInt(e.target.value))}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        >
                            <option value="50">50 trades</option>
                            <option value="100">100 trades</option>
                            <option value="250">250 trades</option>
                            <option value="500">500 trades</option>
                        </select>
                    </div>
                </div>
                <div className="mt-4 flex gap-2">
                    <button
                        onClick={clearFilters}
                        className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 text-gray-700"
                    >
                        Clear Filters
                    </button>
                    <span className="text-sm text-gray-500 self-center ml-2">
                        Showing {trades.length} trade{trades.length !== 1 ? 's' : ''}
                    </span>
                </div>
            </div>

            {/* ==================== VIEW TOGGLE & TRADES TABLE ==================== */}
            <div className="bg-white rounded-xl shadow-lg overflow-hidden">
                <div className="px-6 py-4 bg-gray-50 border-b border-gray-200 flex items-center justify-between">
                    <h2 className="text-xl font-semibold text-gray-800">Trades</h2>
                    <div className="flex items-center bg-gray-200 rounded-lg p-1">
                        <button
                            onClick={() => setViewMode('flat')}
                            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${viewMode === 'flat' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                        >
                            All Trades
                        </button>
                        <button
                            onClick={() => setViewMode('grouped')}
                            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${viewMode === 'grouped' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                        >
                            Grouped Pairs
                        </button>
                    </div>
                </div>
                <div className="overflow-x-auto">
                    {trades.length > 0 ? (
                        viewMode === 'flat' ? (
                            /* ==================== FLAT VIEW ==================== */
                            <table className="w-full">
                                <thead className="bg-gray-50">
                                    <tr>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Symbol</th>
                                        <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">Action</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Quantity</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Price</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Total Cost</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Realized P&L</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Fees</th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Notes</th>
                                    </tr>
                                </thead>
                                <tbody className="bg-white divide-y divide-gray-200">
                                    {trades.map((trade) => (
                                        <tr key={trade.id} className="hover:bg-gray-50 transition-colors duration-150">
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">
                                                {formatDate(trade.tradeDate)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <div className="text-sm font-bold text-gray-900">{trade.symbol}</div>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-center">
                                                <span className={`px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${getActionColor(trade.action)}`}>
                                                    {trade.action}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                                {trade.quantity.toFixed(4)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right font-medium text-gray-900">
                                                {formatCurrency(trade.price)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right font-semibold text-gray-900">
                                                {formatCurrency(trade.totalCost)}
                                            </td>
                                            <td className={`px-6 py-4 whitespace-nowrap text-sm text-right font-semibold ${(trade.realizedPl || 0) > 0 ? 'text-green-600' : (trade.realizedPl || 0) < 0 ? 'text-red-600' : 'text-gray-900'
                                                }`}>
                                                {formatCurrency(trade.realizedPl)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-600">
                                                {formatCurrency(trade.commission + trade.fees)}
                                            </td>
                                            <td className="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">
                                                {trade.notes || '-'}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        ) : (
                            /* ==================== GROUPED VIEW ==================== */
                            <table className="w-full">
                                <thead className="bg-gray-50">
                                    <tr>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-8"></th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Symbol</th>
                                        <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">Pairs</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Total Qty</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Open Qty</th>
                                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Net P&L</th>
                                    </tr>
                                </thead>
                                <tbody className="bg-white divide-y divide-gray-200">
                                    {symbolGroups.map((group) => (
                                        <React.Fragment key={group.symbol}>
                                            {/* Summary row */}
                                            <tr
                                                className="hover:bg-gray-50 cursor-pointer transition-colors duration-150"
                                                onClick={() => toggleExpanded(group.symbol)}
                                            >
                                                <td className="px-6 py-4 text-gray-500">
                                                    <svg className={`w-4 h-4 transition-transform ${expandedSymbols.has(group.symbol) ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                                    </svg>
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap">
                                                    <div className="text-sm font-bold text-gray-900">{group.symbol}</div>
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-center text-sm text-gray-700">
                                                    {group.pairs.length}
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                                    {group.totalQuantity.toFixed(4)}
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                                    {group.openQuantity > 0 ? (
                                                        <span className="text-blue-600 font-medium">{group.openQuantity.toFixed(4)}</span>
                                                    ) : (
                                                        <span className="text-gray-400">-</span>
                                                    )}
                                                </td>
                                                <td className={`px-6 py-4 whitespace-nowrap text-sm text-right font-semibold ${group.netPl > 0 ? 'text-green-600' : group.netPl < 0 ? 'text-red-600' : 'text-gray-900'}`}>
                                                    {formatCurrency(group.netPl)}
                                                </td>
                                            </tr>
                                            {/* Expanded pairs table */}
                                            {expandedSymbols.has(group.symbol) && (
                                                <tr className="bg-gray-50">
                                                    <td className="px-6 py-3"></td>
                                                    <td className="px-6 py-3" colSpan={5}>
                                                        <table className="w-full text-xs">
                                                            <thead>
                                                                <tr className="text-gray-500 border-b border-gray-200">
                                                                    <th className="py-2 text-center font-medium">Status</th>
                                                                    <th className="py-2 text-right font-medium">Qty</th>
                                                                    <th className="py-2 text-left font-medium pl-4">Entry</th>
                                                                    <th className="py-2 text-left font-medium pl-4">Exit</th>
                                                                    <th className="py-2 text-right font-medium">Realized P&L</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody>
                                                                {group.pairs.map((pair, idx) => (
                                                                    <tr key={`${group.symbol}-pair-${idx}`} className="border-b border-gray-100">
                                                                        <td className="py-2 text-center">
                                                                            <span className={`px-2 py-0.5 rounded-full font-semibold ${pair.status === 'closed' ? 'bg-gray-200 text-gray-700' : 'bg-blue-100 text-blue-700'}`}>
                                                                                {pair.status === 'closed' ? 'Closed' : 'Open'}
                                                                            </span>
                                                                        </td>
                                                                        <td className="py-2 text-right text-gray-700">{pair.quantity.toFixed(4)}</td>
                                                                        <td className="py-2 pl-4">
                                                                            <span className="font-medium text-green-700">BUY</span> @ {formatCurrency(pair.entryPrice)}
                                                                            <span className="text-gray-400 ml-2">{formatDateShort(pair.entryTrade.tradeDate)}</span>
                                                                        </td>
                                                                        <td className="py-2 pl-4">
                                                                            {pair.exitTrade ? (
                                                                                <>
                                                                                    <span className="font-medium text-red-700">SELL</span> @ {formatCurrency(pair.exitPrice)}
                                                                                    <span className="text-gray-400 ml-2">{formatDateShort(pair.exitTrade.tradeDate)}</span>
                                                                                </>
                                                                            ) : (
                                                                                <span className="text-gray-400">-</span>
                                                                            )}
                                                                        </td>
                                                                        <td className={`py-2 text-right font-semibold ${pair.realizedPl !== null ? (pair.realizedPl > 0 ? 'text-green-600' : pair.realizedPl < 0 ? 'text-red-600' : 'text-gray-600') : 'text-gray-400'}`}>
                                                                            {pair.realizedPl !== null ? formatCurrency(pair.realizedPl) : '-'}
                                                                        </td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </td>
                                                </tr>
                                            )}
                                        </React.Fragment>
                                    ))}
                                </tbody>
                            </table>
                        )
                    ) : (
                        <div className="px-6 py-16 text-center">
                            <svg className="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">No Trades Found</h3>
                            <p className="text-gray-500 mb-4">
                                {symbolFilter || actionFilter || startDate || endDate
                                    ? 'No trades match your current filters.'
                                    : 'Sync your brokerage account to see your trade history.'}
                            </p>
                            {(symbolFilter || actionFilter || startDate || endDate) && (
                                <button
                                    onClick={clearFilters}
                                    className="text-blue-600 hover:text-blue-800 font-medium"
                                >
                                    Clear Filters
                                </button>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default TradeHistory;
