
import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Database, 
  Image as ImageIcon, 
  Settings, 
  Plus,
  Search,
  LogOut
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface LayoutProps {
  children: React.ReactNode;
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    { icon: LayoutDashboard, label: '概览', path: '/' },
    { icon: Database, label: '数据集', path: '/datasets' },
    { icon: ImageIcon, label: '所有图片', path: '/gallery' },
    { icon: Search, label: '分类检索', path: '/taxonomy' },
    { icon: Settings, label: '设置', path: '/settings' },
  ];

  return (
    <div className="flex h-screen bg-[#F4F7FA] overflow-hidden">
      {/* Sidebar */}
      <motion.aside 
        initial={{ x: -260 }}
        animate={{ x: 0 }}
        className="w-64 bg-white shadow-lg flex flex-col z-20"
      >
        <div className="p-6 flex items-center space-x-3">
          <div className="w-10 h-10 bg-[#12B7F5] rounded-xl flex items-center justify-center shadow-md">
            <ImageIcon className="text-white" size={24} />
          </div>
          <span className="text-xl font-bold text-[#333]">浮游助手</span>
        </div>

        <nav className="flex-1 px-4 space-y-2 mt-4">
          {menuItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl transition-all duration-300 ${
                  isActive 
                    ? 'bg-[#12B7F5] text-white shadow-lg shadow-blue-100' 
                    : 'text-gray-500 hover:bg-blue-50 hover:text-[#12B7F5]'
                }`}
              >
                <item.icon size={20} />
                <span className="font-medium">{item.label}</span>
                {isActive && <motion.div layoutId="active" className="ml-auto w-1.5 h-1.5 rounded-full bg-white" />}
              </button>
            );
          })}
        </nav>

        <div className="p-4 border-t">
          <div className="flex items-center space-x-3 p-3 rounded-xl hover:bg-gray-50 cursor-pointer transition-colors">
            <div className="w-10 h-10 bg-gray-200 rounded-full overflow-hidden">
              <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=Felix" alt="avatar" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900 truncate">邓梓杰</p>
              <p className="text-xs text-gray-500 truncate">江西水利电力大学</p>
            </div>
            <LogOut size={16} className="text-gray-400" />
          </div>
        </div>
      </motion.aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col relative overflow-hidden">
        {/* Header */}
        <header className="h-16 bg-white/80 backdrop-blur-md border-b flex items-center justify-between px-8 z-10">
          <div className="flex items-center space-x-4">
            <h2 className="text-lg font-bold text-gray-800">
              {menuItems.find(item => item.path === location.pathname)?.label || '详情'}
            </h2>
          </div>
          <div className="flex items-center space-x-4">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -transform -translate-y-1/2 text-gray-400" size={18} />
              <input 
                type="text" 
                placeholder="搜索图片或数据集..." 
                className="pl-10 pr-4 py-2 bg-gray-100 border-none rounded-full text-sm focus:ring-2 focus:ring-[#12B7F5] w-64 transition-all"
              />
            </div>
            <button className="bg-[#12B7F5] text-white px-4 py-2 rounded-full text-sm font-medium hover:bg-[#0EA1D9] transition-colors flex items-center space-x-2 shadow-md">
              <Plus size={18} />
              <span>新建项目</span>
            </button>
          </div>
        </header>

        {/* Content Area */}
        <div className="flex-1 overflow-y-auto p-8">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
            >
              {children}
            </motion.div>
          </AnimatePresence>
        </div>
      </main>
    </div>
  );
};

export default Layout;
