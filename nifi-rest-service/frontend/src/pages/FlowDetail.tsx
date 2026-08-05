import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { flowApi } from '../api/flowApi';
import { FlowExecutionHistory } from '../types';
import StatusBadge from '../components/StatusBadge';

const FlowDetail: React.FC = () => {
  const { processGroupId } = useParams<{ processGroupId: string }>();
  const [history, setHistory] = useState<FlowExecutionHistory[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (processGroupId) {
      loadHistory(processGroupId);
    }
  }, [processGroupId]);

  const loadHistory = async (groupId: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await flowApi.getFlowHistory(groupId);
      setHistory(data);
    } catch (err) {
      setError('Ошибка загрузки данных');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  if (!processGroupId) {
    return <div className="text-red-600">Process Group ID не указан</div>;
  }

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
      <div className="flex items-center justify-between">
        <h2 className="text-3xl font-bold text-gray-800">Детали потока</h2>
        <span className="text-sm text-gray-500 font-mono">{processGroupId}</span>
      </div>

      {/* Таблица всех записей */}
      <div className="bg-white rounded-lg shadow-md overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-800">Все записи мониторинга</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ID сессии</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Статус</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Начало</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Завершение</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Длительность</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">FlowFiles</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Callback</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {history.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-4 text-center text-gray-500">
                    Нет записей
                  </td>
                </tr>
              ) : (
                history.map((record) => {
                  const duration = record.completedAt && record.startedAt
                    ? Math.round((new Date(record.completedAt).getTime() - new Date(record.startedAt).getTime()) / 1000)
                    : null;
                  
                  return (
                    <tr key={record.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap text-xs font-mono text-gray-600">
                        {record.monitoringSessionId.slice(0, 8)}...
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <StatusBadge status={record.status} />
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {new Date(record.startedAt).toLocaleString('ru-RU')}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {record.completedAt 
                          ? new Date(record.completedAt).toLocaleString('ru-RU')
                          : '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {duration !== null ? `${duration}s` : '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {record.finalFlowFileCount}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        {record.callbackSent ? (
                          <span className="text-green-600">✓ Отправлен</span>
                        ) : record.callbackUrl ? (
                          <span className="text-yellow-600">Ожидает</span>
                        ) : (
                          <span className="text-gray-400">-</span>
                        )}
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

export default FlowDetail;
