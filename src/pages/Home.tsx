
import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { 
  Image as ImageIcon, 
  Database, 
  ArrowUpRight, 
  Clock,
  ChevronRight
} from 'lucide-react';
import UploadModal from '@/components/UploadModal';

const Home: React.FC = () => {
  const [showUploadModal, setShowUploadModal] = useState(false);
  const stats = [
    { label: '总图片数', value: '1,284', icon: ImageIcon, color: 'bg-blue-500' },
    { label: '数据集', value: '12', icon: Database, color: 'bg-purple-500' },
    { label: '已分类', value: '85%', icon: Clock, color: 'bg-green-500' },
  ];

  const recentDatasets = [
    { name: '赣江采样 2024-01', count: 45, date: '2小时前' },
    { name: '鄱阳湖批次 03', count: 128, date: '昨天' },
    { name: '实验室鉴定 B1', count: 86, date: '3天前' },
  ];

  return (
    <div className="space-y-8">
      {/* Welcome Banner */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-[#12B7F5] to-[#0099FF] p-8 text-white shadow-xl shadow-blue-200">
        <div className="relative z-10 max-w-2xl">
          <h1 className="text-3xl font-bold mb-4">下午好，邓研究员</h1>
          <p className="text-blue-50 opacity-90 mb-6 leading-relaxed">
            欢迎回到浮游动物图片管理助手。今天您已经完成了 3 个数据集的初步鉴定。继续保持高效！
          </p>
          <button 
            onClick={() => setShowUploadModal(true)}
            className="bg-white text-[#12B7F5] px-6 py-2.5 rounded-full font-bold shadow-md hover:bg-blue-50 transition-colors flex items-center space-x-2"
          >
            <span>开始拍照鉴定</span>
            <ArrowUpRight size={18} />
          </button>
        </div>
        {/* Abstract shapes for "灵动" feel */}
        <div className="absolute top-0 right-0 w-64 h-64 bg-white/10 rounded-full -mr-20 -mt-20 blur-3xl" />
        <div className="absolute bottom-0 left-1/2 w-48 h-48 bg-blue-400/20 rounded-full blur-2xl" />
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {stats.map((stat) => (
          <motion.div
            key={stat.label}
            whileHover={{ y: -5 }}
            className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 flex items-center space-x-4"
          >
            <div className={`w-12 h-12 ${stat.color} rounded-2xl flex items-center justify-center text-white shadow-lg`}>
              <stat.icon size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-500">{stat.label}</p>
              <p className="text-2xl font-bold text-gray-800">{stat.value}</p>
            </div>
          </motion.div>
        ))}
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Recent Activity */}
        <div className="lg:col-span-2 bg-white rounded-3xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-lg font-bold text-gray-800">最近数据集</h3>
            <button className="text-[#12B7F5] text-sm font-medium hover:underline">查看全部</button>
          </div>
          <div className="space-y-4">
            {recentDatasets.map((ds) => (
              <div key={ds.name} className="flex items-center justify-between p-4 rounded-2xl hover:bg-gray-50 transition-colors cursor-pointer group">
                <div className="flex items-center space-x-4">
                  <div className="w-12 h-12 bg-blue-50 rounded-xl flex items-center justify-center text-[#12B7F5]">
                    <Database size={24} />
                  </div>
                  <div>
                    <p className="font-semibold text-gray-800">{ds.name}</p>
                    <p className="text-xs text-gray-500">{ds.count} 张图片 • {ds.date}</p>
                  </div>
                </div>
                <ChevronRight size={20} className="text-gray-300 group-hover:text-[#12B7F5] transition-colors" />
              </div>
            ))}
          </div>
        </div>

        {/* Quick Actions / Taxonomy Info */}
        <div className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100">
          <h3 className="text-lg font-bold text-gray-800 mb-6">分类库概览</h3>
          <div className="space-y-6">
            <div className="p-4 bg-orange-50 rounded-2xl border border-orange-100">
              <p className="text-sm font-bold text-orange-700 mb-1">原生动物</p>
              <div className="w-full bg-orange-200 h-2 rounded-full overflow-hidden">
                <div className="bg-orange-500 h-full w-[65%]" />
              </div>
              <p className="text-[10px] text-orange-600 mt-2">176 个已知物种，已覆盖 42%</p>
            </div>
            <div className="p-4 bg-blue-50 rounded-2xl border border-blue-100">
              <p className="text-sm font-bold text-blue-700 mb-1">轮虫类</p>
              <div className="w-full bg-blue-200 h-2 rounded-full overflow-hidden">
                <div className="bg-blue-500 h-full w-[82%]" />
              </div>
              <p className="text-[10px] text-blue-600 mt-2">240 个已知物种，已覆盖 75%</p>
            </div>
            <button className="w-full py-3 bg-gray-50 text-gray-600 rounded-2xl text-sm font-bold hover:bg-gray-100 transition-colors border border-dashed border-gray-300">
              管理分类数据库
            </button>
          </div>
        </div>
      </div>
      
      <UploadModal 
        isOpen={showUploadModal} 
        onClose={() => setShowUploadModal(false)} 
        onSuccess={() => {}} // Home page stats might need update, but for now it's okay
      />
    </div>
  );
};

export default Home;
