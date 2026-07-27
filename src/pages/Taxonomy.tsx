
import React, { useState, useEffect } from 'react';
import { supabase } from '@/lib/supabase';
import { Search, ChevronRight, BookOpen, Filter, Network, Database } from 'lucide-react';

interface Species {
  id: string;
  name_cn: string;
  name_latin: string;
  category: string;
  class_name: string;
  order_name: string;
  family_name: string;
  genus_name: string;
}

const Taxonomy: React.FC = () => {
  const [species, setSpecies] = useState<Species[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'mindmap'>('list');

  useEffect(() => {
    fetchSpecies();
  }, []);

  async function fetchSpecies() {
    setLoading(true);
    const { data, error } = await supabase
      .from('species')
      .select('*')
      .order('name_cn', { ascending: true });

    if (error) {
      console.error('Error fetching species:', error);
    } else {
      setSpecies(data || []);
    }
    setLoading(false);
  }

  const filteredSpecies = species.filter(s => 
    s.name_cn.toLowerCase().includes(searchTerm.toLowerCase()) ||
    s.name_latin.toLowerCase().includes(searchTerm.toLowerCase()) ||
    s.genus_name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">物种分类库</h1>
          <p className="text-sm text-gray-500 mt-1">浏览和管理浮游生物分类学数据库</p>
        </div>
        <div className="bg-white p-1 rounded-2xl shadow-sm border border-gray-100 flex">
          <button 
            onClick={() => setViewMode('list')}
            className={`px-4 py-2 rounded-xl text-sm font-bold flex items-center space-x-2 transition-all ${
              viewMode === 'list' ? 'bg-[#12B7F5] text-white shadow-md' : 'text-gray-500 hover:bg-gray-50'
            }`}
          >
            <Database size={18} />
            <span>列表视图</span>
          </button>
          <button 
            onClick={() => setViewMode('mindmap')}
            className={`px-4 py-2 rounded-xl text-sm font-bold flex items-center space-x-2 transition-all ${
              viewMode === 'mindmap' ? 'bg-[#12B7F5] text-white shadow-md' : 'text-gray-500 hover:bg-gray-50'
            }`}
          >
            <Network size={18} />
            <span>思维导图</span>
          </button>
        </div>
      </div>

      {/* Search and Filters */}
      <div className="flex items-center space-x-4">
        <div className="flex-1 relative bg-white rounded-2xl shadow-sm border border-gray-100 p-2">
          <Search className="absolute left-4 top-1/2 -transform -translate-y-1/2 text-gray-400" size={20} />
          <input 
            type="text" 
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            placeholder="搜索物种中文名、拉丁名、属名..." 
            className="w-full pl-12 pr-4 py-2 bg-transparent border-none focus:ring-0 text-gray-700"
          />
        </div>
        <button className="bg-white p-3 rounded-2xl shadow-sm border border-gray-100 text-gray-500 hover:text-[#12B7F5] transition-colors">
          <Filter size={24} />
        </button>
      </div>

      {viewMode === 'list' ? (
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-gray-50 text-gray-500 text-xs font-bold uppercase tracking-wider">
                  <th className="px-6 py-4">分类/类群</th>
                  <th className="px-6 py-4">中文名</th>
                  <th className="px-6 py-4">拉丁名</th>
                  <th className="px-6 py-4">科/属</th>
                  <th className="px-6 py-4"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {loading ? (
                  [1, 2, 3, 4, 5].map(i => (
                    <tr key={i} className="animate-pulse">
                      <td colSpan={5} className="px-6 py-4 h-16 bg-gray-50/50" />
                    </tr>
                  ))
                ) : (
                  filteredSpecies.map((s) => (
                    <tr key={s.id} className="hover:bg-blue-50/30 transition-colors group cursor-pointer">
                      <td className="px-6 py-4">
                        <span className="px-3 py-1 bg-blue-50 text-[#12B7F5] rounded-full text-[10px] font-bold">
                          {s.category}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <p className="font-bold text-gray-800">{s.name_cn}</p>
                      </td>
                      <td className="px-6 py-4">
                        <p className="text-sm italic text-gray-500">{s.name_latin}</p>
                      </td>
                      <td className="px-6 py-4">
                        <p className="text-xs text-gray-400">{s.family_name} • {s.genus_name}</p>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <ChevronRight size={18} className="text-gray-300 group-hover:text-[#12B7F5] transition-colors inline" />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          {!loading && filteredSpecies.length === 0 && (
            <div className="py-20 text-center">
              <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <BookOpen className="text-gray-300" size={32} />
              </div>
              <p className="text-gray-500">未找到匹配的物种</p>
            </div>
          )}
        </div>
      ) : (
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 h-[600px] flex items-center justify-center relative overflow-hidden">
          {/* Placeholder for Mind Map */}
          <div className="absolute inset-0 opacity-10 pointer-events-none">
            <div className="w-full h-full bg-[radial-gradient(#12B7F5_1px,transparent_1px)] [background-size:20px_20px]" />
          </div>
          <div className="text-center z-10">
            <div className="w-20 h-20 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-6 text-[#12B7F5]">
              <Network size={48} />
            </div>
            <h3 className="text-xl font-bold text-gray-800 mb-2">思维导图生成中</h3>
            <p className="text-gray-500 max-w-xs mx-auto">我们将基于当前的物种分类层级（门、纲、目、科、属、种）为您生成动态可视化的分类树。</p>
            <button className="mt-8 px-8 py-3 bg-[#12B7F5] text-white rounded-full font-bold shadow-lg hover:bg-[#0EA1D9] transition-all">
              立即生成
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default Taxonomy;
