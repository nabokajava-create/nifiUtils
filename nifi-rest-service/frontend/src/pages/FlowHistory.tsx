import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { flowApi } from '../api/flowApi';
import { FlowExecutionHistory, FlowStatistics } from '../types';
import StatusBadge from '../components/StatusBadge';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, BarChart, Bar } from 'recharts';

const FlowHistory: React.FC = () => {
  const [processGroups, setProcessGroups] = useState<string[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<string>('');
  const [history, setHistory] = useState<FlowExecutionHistory[]>([]);
  const [statistics, setStatistics] = useState<FlowStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadProcessGroups = async () => {
      try {
        const groups = await flowApi.getAllProcessGroups();
        setProcessGroups(groups);
        if (groups.length > 0) {
          setSelectedGroup(groups[0]);
        }
      } catch (err) {
        setError('Ошибка загрузки списка потоков');
        console.error(err);
      }
    };
    loadProcessGroups();
  }, []);

  useEffect(() => {
    if (selectedGroup) {
      loadData(selectedGroup);
    }
  }, [selectedGroup]);

  const loadData = async (groupId: string) => {
    setLoading(true);
    setError(null);
    try {
      const [historyData, stats] = await Promise.all([
        flowApi.getFlowHistory(groupId),
        flowApi.getFlowStatistics(groupId),
      ]);
      setHistory(historyData);
      setStatistics(stats);
    } catch (err) {
      setError('Ошибка загрузки данных истории');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const chartData = history
    .filter(h => h.status === 'COMPLETED' && h.startedAt && h.completedAt)
    .map(h => ({
      name: new Date(h.startedAt).toLocaleDateString('ru-RU', { 
        day: '2-digit', 
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      }),
      duration: h.completedAt && h.startedAt 
        ? Math.round((new Date(h.completedAt).getTime() - new Date(h.startedAt).getTime()) / 1000)
        : 0,
      flowFiles: h.finalFlowFileCount,
    }))
    .slice(-10); // Последние 10 запусков

  return (
    <div className="space-y-6">
      <h2 className="text-3xl font-bold text-gray-800">История запусков потоков</h2>

      {/* Выбор потока */}
      <div className="bg-white p-4 rounded-lg shadow-md">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Выберите Process Group:
        </label>
        <select
          value={selectedGroup}
          onChange={(e) => setSelectedGroup(e.target.value)}
          className="w-full md:w-96 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {processGroups.map(group => (
            <option key={group} value={group}>{group}</option>
          ))}
        </select>
      </div>

      {loading && (
        <div className="flex justify-center items-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
        </div>
      )}

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {!loading && !error && statistics && (
        <>
          {/* Статистика */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="bg-white p-6 rounded-lg shadow-md">
              <h3 className="text-sm font-medium text-gray-500">Всего запусков</h3>
              <p className="text-3xl font-bold text-gray-900">{statistics.totalExecutions}</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow-md">
              <h3 className="text-sm font-medium text-gray-500">Успешных</h3>
              <p className="text-3xl font-bold text-green-600">{statistics.successfulExecutions}</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow-md">
              <h3 className="text-sm font-medium text-gray-500">Неудачных</h3>
              <p className="text-3xl font-bold text-red-600">{statistics.failedExecutions}</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow-md">
              <h3 className="text-sm font-medium text-gray-500">Процент успеха</h3>
              <p className="text-3xl font-bold text-blue-600">{Math.round(statistics.successRate)}%</p>
            </div>
          </div>

          {/* Графики */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white p-6 rounded-lg shadow-md">
              <h3 className="text-lg font-semibold text-gray-800 mb-4">Длительность выполнения (последние 10 запусков)</h3>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" angle={-45} textAnchor="end" height={80} />
                  <YAxis label={{ value: 'сек', angle: -90, position: 'insideLeft' }} />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="duration" stroke="#3B82F6" name="Длительность (сек)" />
                </LineChart>
              </ResponsiveContainer>
            </div>

            <div className="bg-white p-6 rounded-lg shadow-md">
              <h3 className="text-lg font-semibold text-gray-800 mb-4">Количество FlowFiles (последние 10 запусков)</h3>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" angle={-45} textAnchor="end" height={80} />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="flowFiles" fill="#10B981" name="FlowFiles" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </>
      )}

      {/* Таблица истории */}
      <div className="bg-white rounded-lg shadow-md overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-800">История запусков</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Дата запуска</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Статус</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Длительность</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">FlowFiles</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Процессоры</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Действия</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {history.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-4 text-center text-gray-500">
                    Нет данных истории
                  </td>
                </tr>
              ) : (
                history.slice(0, 50).map((record) => {
                  const duration = record.completedAt && record.startedAt
                    ? Math.round((new Date(record.completedAt).getTime() - new Date(record.startedAt).getTime()) / 1000)
                    : null;
                  
                  return (
                    <tr key={record.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {new Date(record.startedAt).toLocaleString('ru-RU')}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <StatusBadge status={record.status} />
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {duration !== null ? `${duration}s` : '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {record.finalFlowFileCount}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {record.activeProcessorCount}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                        <Link
                          to={`/flow/${record.processGroupId}`}
                          className="text-blue-600 hover:text-blue-900"
                        >
                          Детали
                        </Link>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default FlowHistory;
