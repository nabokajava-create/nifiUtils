import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { flowApi } from '../api/flowApi';
import { FlowExecutionHistory, DashboardStats } from '../types';
import StatusBadge from '../components/StatusBadge';

const Dashboard: React.FC = () => {
  const [activeSessions, setActiveSessions] = useState<FlowExecutionHistory[]>([]);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [sessions, dashboardStats] = await Promise.all([
          flowApi.getActiveSessions(),
          flowApi.getDashboardStats(),
        ]);
        setActiveSessions(sessions);
        setStats(dashboardStats);
      } catch (err) {
        setError('Ошибка загрузки данных дашборда');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    loadData();
    const interval = setInterval(loadData, 10000); // Обновление каждые 10 секунд
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h2 className="text-3xl font-bold text-gray-800">Дашборд мониторинга</h2>

      {/* Карточки статистики */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div className="bg-white p-6 rounded-lg shadow-md">
            <h3 className="text-sm font-medium text-gray-500">Всего потоков</h3>
            <p className="text-3xl font-bold text-gray-900">{stats.totalFlows}</p>
          </div>
          <div className="bg-white p-6 rounded-lg shadow-md">
            <h3 className="text-sm font-medium text-gray-500">Активные сейчас</h3>
            <p className="text-3xl font-bold text-yellow-600">{stats.activeFlows}</p>
          </div>
          <div className="bg-white p-6 rounded-lg shadow-md">
            <h3 className="text-sm font-medium text-gray-500">Завершено сегодня</h3>
            <p className="text-3xl font-bold text-green-600">{stats.completedToday}</p>
          </div>
          <div className="bg-white p-6 rounded-lg shadow-md">
            <h3 className="text-sm font-medium text-gray-500">Среднее время выполнения</h3>
            <p className="text-3xl font-bold text-blue-600">
              {Math.round(stats.averageDurationMs / 1000)}s
            </p>
          </div>
        </div>
      )}

      {/* Таблица активных сессий */}
      <div className="bg-white rounded-lg shadow-md overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-800">Активные сессии мониторинга</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Process Group
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Статус
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Начато
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  FlowFiles
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Процессоры
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Действия
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {activeSessions.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-4 text-center text-gray-500">
                    Нет активных сессий мониторинга
                  </td>
                </tr>
              ) : (
                activeSessions.map((session) => (
                  <tr key={session.monitoringSessionId} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">
                        {session.processGroupName || session.processGroupId}
                      </div>
                      <div className="text-sm text-gray-500">{session.processGroupId}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <StatusBadge status={session.status} />
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {new Date(session.startedAt).toLocaleString('ru-RU')}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {session.finalFlowFileCount}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {session.activeProcessorCount}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                      <Link
                        to={`/flow/${session.processGroupId}`}
                        className="text-blue-600 hover:text-blue-900 mr-4"
                      >
                        Детали
                      </Link>
                      <button
                        onClick={() => flowApi.stopMonitoring(session.monitoringSessionId)}
                        className="text-red-600 hover:text-red-900"
                      >
                        Остановить
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
