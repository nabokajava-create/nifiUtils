import { Routes, Route, Link } from 'react-router-dom'
import Dashboard from './pages/Dashboard'
import FlowHistory from './pages/FlowHistory'
import FlowDetail from './pages/FlowDetail'

function App() {
  return (
    <div className="min-h-screen bg-gray-100">
      <nav className="bg-blue-600 text-white p-4 shadow-lg">
        <div className="container mx-auto flex justify-between items-center">
          <h1 className="text-2xl font-bold">NiFi Flow Monitor</h1>
          <div className="space-x-4">
            <Link to="/" className="hover:text-blue-200 transition">Dashboard</Link>
            <Link to="/history" className="hover:text-blue-200 transition">История запусков</Link>
          </div>
        </div>
      </nav>
      
      <main className="container mx-auto p-6">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/history" element={<FlowHistory />} />
          <Route path="/flow/:processGroupId" element={<FlowDetail />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
