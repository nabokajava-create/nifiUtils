import axios from 'axios';
import { 
  FlowExecutionHistory, 
  FlowMonitoringRequest, 
  DashboardStats,
  FlowStatistics 
} from '../types';

const API_BASE = '/api/nifi';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const flowApi = {
  // Запуск мониторинга потока
  startMonitoring: async (request: FlowMonitoringRequest): Promise<{ monitoringSessionId: string }> => {
    const response = await api.post('/flow-monitor/start', request);
    return response.data;
  },

  // Получение статуса мониторинга
  getMonitoringStatus: async (monitoringSessionId: string): Promise<FlowExecutionHistory> => {
    const response = await api.get(`/flow-monitor/${monitoringSessionId}/status`);
    return response.data;
  },

  // Остановка мониторинга
  stopMonitoring: async (monitoringSessionId: string): Promise<void> => {
    await api.post(`/flow-monitor/${monitoringSessionId}/stop`);
  },

  // Получение активных сессий мониторинга
  getActiveSessions: async (): Promise<FlowExecutionHistory[]> => {
    const response = await api.get('/flow-monitor/active');
    return response.data;
  },

  // Получение истории запусков для конкретного потока
  getFlowHistory: async (
    processGroupId: string, 
    startDate?: string, 
    endDate?: string
  ): Promise<FlowExecutionHistory[]> => {
    const params: any = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    
    const response = await api.get(`/flow-history/${processGroupId}`, { params });
    return response.data;
  },

  // Получение статистики по потоку
  getFlowStatistics: async (processGroupId: string): Promise<FlowStatistics> => {
    const response = await api.get(`/flow-statistics/${processGroupId}`);
    return response.data;
  },

  // Получение общей статистики для дашборда
  getDashboardStats: async (): Promise<DashboardStats> => {
    const response = await api.get('/dashboard/stats');
    return response.data;
  },

  // Получение всех уникальных Process Group ID
  getAllProcessGroups: async (): Promise<string[]> => {
    const response = await api.get('/process-groups');
    return response.data;
  },
};

export default flowApi;
