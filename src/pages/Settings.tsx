
import React from 'react';
import { User, School, Info, Shield, Bell, Github } from 'lucide-react';

const Settings: React.FC = () => {
  const sections = [
    {
      title: '个人资料',
      icon: User,
      items: [
        { label: '账号信息', value: 'dengzijie@example.com' },
        { label: '身份认证', value: '已认证研究员' },
      ]
    },
    {
      title: '单位信息',
      icon: School,
      items: [
        { label: '开发单位', value: '江西水利电力大学 - 江西师范大学' },
        { label: '实验室', value: '浮游生物生态研究室' },
      ]
    },
    {
      title: 'AI 助手配置',
      icon: Shield,
      items: [
        { label: 'API 接口', value: 'OpenAI 兼容' },
        { label: 'API Key', value: 'sk-••••••••' },
      ]
    },
    {
      title: '关于程序',
      icon: Info,
      items: [
        { label: '软件版本', value: 'v1.0.0 (Beta)' },
        { label: '开发人员', value: '邓梓杰' },
        { label: '包名', value: 'com.dlovel' },
      ]
    }
  ];

  return (
    <div className="max-w-4xl mx-auto space-y-8">
      <div className="flex items-center space-x-6 bg-white p-8 rounded-3xl shadow-sm border border-gray-100">
        <div className="w-24 h-24 bg-blue-50 rounded-3xl overflow-hidden shadow-inner border-4 border-white">
          <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=Felix" alt="avatar" />
        </div>
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-gray-800">邓梓杰</h1>
          <p className="text-gray-500">江西水利电力大学 • 研究生</p>
          <div className="flex space-x-3 mt-4">
            <button className="px-4 py-1.5 bg-[#12B7F5] text-white rounded-full text-xs font-bold hover:bg-[#0EA1D9] transition-colors shadow-md">
              编辑资料
            </button>
            <button className="px-4 py-1.5 bg-gray-100 text-gray-600 rounded-full text-xs font-bold hover:bg-gray-200 transition-colors">
              切换账号
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <div className="space-y-6">
          {sections.map((section) => (
            <div key={section.title} className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100">
              <div className="flex items-center space-x-3 mb-6">
                <div className="w-8 h-8 bg-blue-50 rounded-lg flex items-center justify-center text-[#12B7F5]">
                  <section.icon size={18} />
                </div>
                <h3 className="font-bold text-gray-800">{section.title}</h3>
              </div>
              <div className="space-y-4">
                {section.items.map((item) => (
                  <div key={item.label} className="flex justify-between items-center group">
                    <span className="text-sm text-gray-400">{item.label}</span>
                    <span className="text-sm font-semibold text-gray-700 group-hover:text-[#12B7F5] transition-colors">{item.value}</span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="space-y-6">
          <div className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100">
            <h3 className="font-bold text-gray-800 mb-6">应用设置</h3>
            <div className="space-y-2">
              <button className="w-full flex items-center justify-between p-4 rounded-2xl hover:bg-gray-50 transition-colors">
                <div className="flex items-center space-x-3 text-gray-600">
                  <Bell size={18} />
                  <span className="text-sm font-medium">消息通知</span>
                </div>
                <div className="w-10 h-5 bg-blue-500 rounded-full relative">
                  <div className="absolute right-1 top-1 w-3 h-3 bg-white rounded-full" />
                </div>
              </button>
              <button className="w-full flex items-center justify-between p-4 rounded-2xl hover:bg-gray-50 transition-colors">
                <div className="flex items-center space-x-3 text-gray-600">
                  <Shield size={18} />
                  <span className="text-sm font-medium">隐私与安全</span>
                </div>
                <div className="text-gray-300">
                  <Info size={18} />
                </div>
              </button>
              <button className="w-full flex items-center justify-between p-4 rounded-2xl hover:bg-gray-50 transition-colors">
                <div className="flex items-center space-x-3 text-[#12B7F5]">
                  <Github size={18} />
                  <span className="text-sm font-medium">开源仓库</span>
                </div>
              </button>
            </div>
          </div>

          <div className="bg-gradient-to-br from-blue-50 to-white rounded-3xl p-6 border border-blue-100 text-center">
            <p className="text-xs text-blue-400 font-bold uppercase tracking-widest mb-2">Developed with Care</p>
            <p className="text-sm text-gray-600 leading-relaxed">
              专门为生态环境监测打造的<br/>
              高效浮游生物鉴定助手
            </p>
            <div className="mt-4 flex justify-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-blue-200" />
              <span className="w-2 h-2 rounded-full bg-blue-400" />
              <span className="w-2 h-2 rounded-full bg-blue-600" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Settings;
