
import React, { useState, useEffect } from 'react';
import { supabase } from '@/lib/supabase';
import { motion } from 'framer-motion';
import { Plus, Database, Calendar, Image as ImageIcon, MoreVertical, Search } from 'lucide-react';
import type { DatasetRecord } from '@/lib/domain';

interface Dataset extends DatasetRecord {
  image_count?: number;
}

interface DatasetRow extends Dataset {
  plankton_images?: Array<{ count: number | null }>;
}

interface DatasetDraft {
  name: string;
  description: string;
  sampling_site: string;
  sample_code: string;
  sampled_at: string;
  latitude: string;
  longitude: string;
  water_depth_meters: string;
  water_temperature_celsius: string;
  ph: string;
  salinity_psu: string;
}

const emptyDataset: DatasetDraft = {
  name: '', description: '', sampling_site: '', sample_code: '', sampled_at: '',
  latitude: '', longitude: '', water_depth_meters: '', water_temperature_celsius: '',
  ph: '', salinity_psu: ''
};

const nullableNumber = (value: string) => value.trim() === '' ? null : Number(value);

const Datasets: React.FC = () => {
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [newDataset, setNewDataset] = useState<DatasetDraft>(emptyDataset);

  useEffect(() => {
    fetchDatasets();
  }, []);

  async function fetchDatasets() {
    setLoading(true);
    const { data, error } = await supabase
      .from('datasets')
      .select('*, plankton_images(count)')
      .order('created_at', { ascending: false });

    if (error) {
      console.error('Error fetching datasets:', error);
    } else {
      const formattedData = (data ?? []).map((ds: DatasetRow) => ({
        ...ds,
        image_count: ds.plankton_images?.[0]?.count || 0
      }));
      setDatasets(formattedData);
    }
    setLoading(false);
  }

  async function createDataset() {
    if (!newDataset.name) return;
    const { error } = await supabase.from('datasets').insert([{
      ...newDataset,
      name: newDataset.name.trim(),
      description: newDataset.description.trim() || null,
      sampling_site: newDataset.sampling_site.trim() || null,
      sample_code: newDataset.sample_code.trim() || null,
      sampled_at: newDataset.sampled_at || null,
      latitude: nullableNumber(newDataset.latitude),
      longitude: nullableNumber(newDataset.longitude),
      water_depth_meters: nullableNumber(newDataset.water_depth_meters),
      water_temperature_celsius: nullableNumber(newDataset.water_temperature_celsius),
      ph: nullableNumber(newDataset.ph),
      salinity_psu: nullableNumber(newDataset.salinity_psu),
    }]);
    if (error) {
      console.error('Error creating dataset:', error);
    } else {
      setShowModal(false);
      setNewDataset(emptyDataset);
      fetchDatasets();
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">数据集管理</h1>
          <p className="text-sm text-gray-500 mt-1">创建和管理您的采样批次与实验室项目</p>
        </div>
        <button 
          onClick={() => setShowModal(true)}
          className="bg-[#12B7F5] text-white px-6 py-2.5 rounded-full font-bold shadow-lg shadow-blue-100 hover:bg-[#0EA1D9] transition-all flex items-center space-x-2"
        >
          <Plus size={20} />
          <span>新建数据集</span>
        </button>
      </div>

      {/* Filter Bar */}
      <div className="flex items-center space-x-4 bg-white p-2 rounded-2xl shadow-sm border border-gray-100">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -transform -translate-y-1/2 text-gray-400" size={18} />
          <input 
            type="text" 
            placeholder="搜索数据集名称或描述..." 
            className="w-full pl-10 pr-4 py-2 bg-transparent border-none focus:ring-0 text-sm"
          />
        </div>
        <div className="h-6 w-[1px] bg-gray-200" />
        <button className="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-50 rounded-xl transition-colors">
          按时间排序
        </button>
        <button className="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-50 rounded-xl transition-colors">
          按数量排序
        </button>
      </div>

      {/* Datasets Grid */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map(i => (
            <div key={i} className="bg-white h-48 rounded-3xl animate-pulse border border-gray-100" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {datasets.map((ds) => (
            <motion.div
              key={ds.id}
              whileHover={{ y: -5 }}
              className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100 hover:shadow-xl hover:shadow-blue-50/50 transition-all cursor-pointer group"
            >
              <div className="flex items-start justify-between mb-4">
                <div className="w-12 h-12 bg-blue-50 rounded-2xl flex items-center justify-center text-[#12B7F5] group-hover:bg-[#12B7F5] group-hover:text-white transition-colors">
                  <Database size={24} />
                </div>
                <button className="text-gray-400 hover:text-gray-600">
                  <MoreVertical size={20} />
                </button>
              </div>
              <h3 className="text-lg font-bold text-gray-800 mb-1 group-hover:text-[#12B7F5] transition-colors">{ds.name}</h3>
              <p className="text-sm text-gray-500 line-clamp-2 mb-4 h-10">{ds.description || '暂无描述'}</p>
              {(ds.sampling_site || ds.sample_code) && (
                <p className="text-xs text-[#0EA1D9] mb-3 truncate">
                  {[ds.sample_code && `样品 ${ds.sample_code}`, ds.sampling_site].filter(Boolean).join(' · ')}
                </p>
              )}
              
              <div className="flex items-center justify-between pt-4 border-t border-gray-50">
                <div className="flex items-center space-x-2 text-xs text-gray-400">
                  <Calendar size={14} />
                  <span>{new Date(ds.created_at).toLocaleDateString()}</span>
                </div>
                <div className="flex items-center space-x-2 text-xs font-bold text-[#12B7F5]">
                  <ImageIcon size={14} />
                  <span>{ds.image_count} 张照片</span>
                </div>
              </div>
            </motion.div>
          ))}
          {datasets.length === 0 && (
            <div className="col-span-full py-20 text-center bg-white rounded-3xl border-2 border-dashed border-gray-200">
              <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <Plus className="text-gray-300" size={32} />
              </div>
              <p className="text-gray-500">还没有数据集，点击右上角新建一个吧</p>
            </div>
          )}
        </div>
      )}

      {/* Create Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <motion.div 
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            className="bg-white w-full max-w-md max-h-[90vh] overflow-y-auto rounded-3xl p-8 shadow-2xl"
          >
            <h3 className="text-xl font-bold text-gray-800 mb-6">新建数据集</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">项目名称</label>
                <input 
                  type="text" 
                  value={newDataset.name}
                  onChange={e => setNewDataset({ ...newDataset, name: e.target.value })}
                  placeholder="例如：赣江北支采样-202401"
                  className="w-full px-4 py-3 bg-gray-50 border-none rounded-2xl focus:ring-2 focus:ring-[#12B7F5] transition-all"
                />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {[
                  ['sampling_site', '采样地点', '例如：赣江北支'],
                  ['sample_code', '样品编号', '例如：GJ-20260728-01'],
                  ['sampled_at', '采样时间', '2026-07-28T09:30'],
                  ['latitude', '纬度', '例如：28.682'],
                  ['longitude', '经度', '例如：115.883'],
                  ['water_depth_meters', '水深（m）', '例如：2.5'],
                  ['water_temperature_celsius', '水温（℃）', '例如：24.6'],
                  ['ph', 'pH', '例如：7.2'],
                  ['salinity_psu', '盐度（PSU）', '例如：0.3'],
                ].map(([key, label, placeholder]) => (
                  <div key={key}>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
                    <input
                      type={key === 'sampled_at' ? 'datetime-local' : 'text'}
                      value={newDataset[key as keyof DatasetDraft]}
                      onChange={e => setNewDataset({ ...newDataset, [key]: e.target.value })}
                      placeholder={placeholder}
                      className="w-full px-4 py-3 bg-gray-50 border-none rounded-2xl focus:ring-2 focus:ring-[#12B7F5] transition-all"
                    />
                  </div>
                ))}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">项目描述</label>
                <textarea 
                  value={newDataset.description}
                  onChange={e => setNewDataset({ ...newDataset, description: e.target.value })}
                  placeholder="输入采样地点、目的或批次详情..."
                  rows={3}
                  className="w-full px-4 py-3 bg-gray-50 border-none rounded-2xl focus:ring-2 focus:ring-[#12B7F5] transition-all resize-none"
                />
              </div>
            </div>
            <div className="flex space-x-3 mt-8">
              <button 
                onClick={() => setShowModal(false)}
                className="flex-1 py-3 bg-gray-100 text-gray-600 rounded-2xl font-bold hover:bg-gray-200 transition-colors"
              >
                取消
              </button>
              <button 
                onClick={createDataset}
                disabled={!newDataset.name}
                className="flex-1 py-3 bg-[#12B7F5] text-white rounded-2xl font-bold shadow-lg shadow-blue-100 hover:bg-[#0EA1D9] transition-all disabled:opacity-50"
              >
                创建项目
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
};

export default Datasets;
