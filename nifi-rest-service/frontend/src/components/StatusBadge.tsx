import React from 'react';
import { ExecutionStatus } from '../types';

interface StatusBadgeProps {
  status: ExecutionStatus;
}

const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const getStatusConfig = (status: ExecutionStatus) => {
    switch (status) {
      case 'RUNNING':
        return { 
          color: 'bg-yellow-100 text-yellow-800 border-yellow-300', 
          label: 'Выполняется',
          icon: '🟡'
        };
      case 'COMPLETED':
        return { 
          color: 'bg-green-100 text-green-800 border-green-300', 
          label: 'Завершено',
          icon: '🟢'
        };
      case 'NOT_STARTED':
        return { 
          color: 'bg-gray-100 text-gray-800 border-gray-300', 
          label: 'Ожидание',
          icon: '⚪'
        };
      case 'TIMEOUT':
        return { 
          color: 'bg-orange-100 text-orange-800 border-orange-300', 
          label: 'Таймаут',
          icon: '🟠'
        };
      case 'ERROR':
        return { 
          color: 'bg-red-100 text-red-800 border-red-300', 
          label: 'Ошибка',
          icon: '🔴'
        };
      default:
        return { 
          color: 'bg-gray-100 text-gray-800 border-gray-300', 
          label: status,
          icon: '⚪'
        };
    }
  };

  const config = getStatusConfig(status);

  return (
    <span className={`inline-flex items-center px-3 py-1 rounded-full text-sm font-medium border ${config.color}`}>
      <span className="mr-2">{config.icon}</span>
      {config.label}
    </span>
  );
};

export default StatusBadge;
