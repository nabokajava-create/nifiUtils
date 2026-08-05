export interface FlowExecutionHistory {
  id: number;
  monitoringSessionId: string;
  processGroupId: string;
  processGroupName: string;
  status: ExecutionStatus;
  startedAt: string;
  completedAt?: string;
  firstActivityAt?: string;
  initialFlowFileCount: number;
  finalFlowFileCount: number;
  totalQueueSizeBytes: number;
  activeProcessorCount: number;
  emptyCheckCount: number;
  consecutiveEmptyChecks: number;
  callbackUrl?: string;
  callbackSent: boolean;
  callbackAttempts: number;
  lastCallbackAttempt?: string;
  callbackResponseCode?: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export type ExecutionStatus = 
  | 'NOT_STARTED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'TIMEOUT'
  | 'ERROR';

export interface QueueStatus {
  connectionId: string;
  flowFileCount: number;
  queueSizeBytes: number;
  sourceProcessorName: string;
  destinationProcessorName: string;
}

export interface FlowCompletionConfig {
  maxWaitTimeMs: number;
  checkIntervalMs: number;
  emptyQueueThreshold: number;
  consecutiveEmptyChecksRequired: number;
  considerOnlyActiveProcessors: boolean;
}

export interface FlowMonitoringRequest {
  processGroupId: string;
  callbackUrl?: string;
  config?: FlowCompletionConfig;
}

export interface FlowMonitoringResult {
  monitoringSessionId: string;
  processGroupId: string;
  processGroupName: string;
  status: ExecutionStatus;
  startedAt: string;
  completedAt?: string;
  durationMs?: number;
  metrics: {
    initialFlowFileCount: number;
    finalFlowFileCount: number;
    peakFlowFileCount: number;
    averageQueueSizeBytes: number;
    maxQueueSizeBytes: number;
    emptyCheckCount: number;
  };
}

export interface DashboardStats {
  totalFlows: number;
  activeFlows: number;
  completedToday: number;
  failedToday: number;
  averageDurationMs: number;
}

export interface FlowStatistics {
  processGroupId: string;
  processGroupName: string;
  totalExecutions: number;
  successfulExecutions: number;
  failedExecutions: number;
  averageDurationMs: number;
  lastExecutionAt?: string;
  successRate: number;
}
