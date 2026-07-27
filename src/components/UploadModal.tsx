
import React, { useState, useEffect } from 'react';
import { supabase } from '@/lib/supabase';
import { motion } from 'framer-motion';
import { X, Upload, Check, Loader2, Plus } from 'lucide-react';
import SpeciesAutocomplete from './SpeciesAutocomplete';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

interface UploadItem {
  file: File;
  preview: string;
  speciesId?: string;
  speciesName?: string;
  status: 'pending' | 'uploading' | 'success' | 'error';
}

const UploadModal: React.FC<Props> = ({ isOpen, onClose, onSuccess }) => {
  const [items, setItems] = useState<UploadItem[]>([]);
  const [datasets, setDatasets] = useState<{ id: string, name: string }[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState<string>('');
  const [isUploading, setIsUploading] = useState(false);

  useEffect(() => {
    if (isOpen) {
      fetchDatasets();
    }
  }, [isOpen]);

  async function fetchDatasets() {
    const { data } = await supabase.from('datasets').select('id, name').order('name');
    if (data) {
      setDatasets(data);
      if (data.length > 0) setSelectedDatasetId(data[0].id);
    }
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const newFiles = Array.from(e.target.files);
      const newItems: UploadItem[] = newFiles.map(file => ({
        file,
        preview: URL.createObjectURL(file),
        status: 'pending'
      }));
      setItems(prev => [...prev, ...newItems]);
    }
  };

  const removeItem = (index: number) => {
    setItems(prev => {
      const newItems = [...prev];
      URL.revokeObjectURL(newItems[index].preview);
      newItems.splice(index, 1);
      return newItems;
    });
  };

  const updateItemSpecies = (index: number, species: { id: string, name_cn: string }) => {
    setItems(prev => {
      const newItems = [...prev];
      newItems[index].speciesId = species.id;
      newItems[index].speciesName = species.name_cn;
      return newItems;
    });
  };

  async function handleUpload() {
    if (!selectedDatasetId || items.length === 0) return;
    setIsUploading(true);

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.status === 'success') continue;

      setItems(prev => {
        const newItems = [...prev];
        newItems[i].status = 'uploading';
        return newItems;
      });

      try {
        const fileExt = item.file.name.split('.').pop();
        const fileName = `${Math.random().toString(36).substring(2)}_${Date.now()}.${fileExt}`;
        const filePath = `uploads/${fileName}`;

        const { error: uploadError } = await supabase.storage
          .from('plankton-images')
          .upload(filePath, item.file);

        if (uploadError) throw uploadError;

        const { data: { publicUrl } } = supabase.storage
          .from('plankton-images')
          .getPublicUrl(filePath);

        const { error: dbError } = await supabase.from('plankton_images').insert({
          dataset_id: selectedDatasetId,
          species_id: item.speciesId,
          image_url: publicUrl,
          custom_name: item.file.name,
          is_favorite: false,
          review_status: 'UNREVIEWED',
        });

        if (dbError) throw dbError;

        setItems(prev => {
          const newItems = [...prev];
          newItems[i].status = 'success';
          return newItems;
        });
      } catch (error) {
        console.error('Upload failed:', error);
        setItems(prev => {
          const newItems = [...prev];
          newItems[i].status = 'error';
          return newItems;
        });
      }
    }

    setIsUploading(false);
    if (items.every(item => item.status === 'success' || item.status === 'error')) {
      setTimeout(() => {
        onSuccess();
        onClose();
        setItems([]);
      }, 1500);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <motion.div 
        initial={{ scale: 0.9, opacity: 0, y: 20 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        className="bg-white w-full max-w-4xl rounded-3xl shadow-2xl flex flex-col max-h-[90vh]"
      >
        {/* Header */}
        <div className="p-6 border-b flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center text-[#12B7F5]">
              <Upload size={20} />
            </div>
            <div>
              <h3 className="text-xl font-bold text-gray-800">上传鉴定照片</h3>
              <p className="text-xs text-gray-500">支持批量上传，并可预设物种分类</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 hover:bg-gray-100 rounded-full transition-colors">
            <X size={24} className="text-gray-400" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Dataset Selector */}
          <div className="bg-gray-50 p-4 rounded-2xl border border-gray-100 flex items-center space-x-4">
            <span className="text-sm font-bold text-gray-600">保存至数据集:</span>
            <select 
              value={selectedDatasetId}
              onChange={e => setSelectedDatasetId(e.target.value)}
              className="flex-1 bg-white border-none rounded-xl text-sm focus:ring-2 focus:ring-[#12B7F5] py-2 px-4 shadow-sm"
            >
              {datasets.map(ds => (
                <option key={ds.id} value={ds.id}>{ds.name}</option>
              ))}
              {datasets.length === 0 && <option disabled>请先创建数据集</option>}
            </select>
          </div>

          {/* File Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {items.map((item, idx) => (
              <div key={idx} className="bg-white border rounded-2xl p-3 flex space-x-4 group relative">
                <div className="w-24 h-24 rounded-xl overflow-hidden bg-gray-100 flex-shrink-0 relative">
                  <img src={item.preview} className="w-full h-full object-cover" alt="preview" />
                  {item.status === 'uploading' && (
                    <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                      <Loader2 className="text-white animate-spin" size={24} />
                    </div>
                  )}
                  {item.status === 'success' && (
                    <div className="absolute inset-0 bg-green-500/40 flex items-center justify-center">
                      <Check className="text-white" size={24} />
                    </div>
                  )}
                </div>
                <div className="flex-1 min-w-0 space-y-2">
                  <p className="text-xs font-bold text-gray-400 truncate">{item.file.name}</p>
                  <SpeciesAutocomplete 
                    onSelect={(s) => updateItemSpecies(idx, s)}
                    placeholder="关联物种..."
                  />
                </div>
                {!isUploading && (
                  <button 
                    onClick={() => removeItem(idx)}
                    className="absolute -top-2 -right-2 bg-white shadow-md rounded-full p-1 text-red-400 hover:text-red-600 border border-red-50"
                  >
                    <X size={14} />
                  </button>
                )}
              </div>
            ))}

            {/* Add More Button */}
            <label className="border-2 border-dashed border-gray-200 rounded-2xl p-8 flex flex-col items-center justify-center cursor-pointer hover:border-[#12B7F5] hover:bg-blue-50/30 transition-all">
              <input type="file" multiple accept="image/*" className="hidden" onChange={handleFileSelect} />
              <div className="w-12 h-12 bg-blue-50 rounded-full flex items-center justify-center text-[#12B7F5] mb-2">
                <Plus size={24} />
              </div>
              <span className="text-sm font-bold text-gray-500">添加照片</span>
            </label>
          </div>
        </div>

        {/* Footer */}
        <div className="p-6 border-t flex items-center justify-between">
          <div className="text-sm text-gray-500">
            共选择 <span className="font-bold text-[#12B7F5]">{items.length}</span> 张照片
          </div>
          <div className="flex space-x-3">
            <button 
              onClick={onClose}
              disabled={isUploading}
              className="px-6 py-2.5 bg-gray-100 text-gray-600 rounded-full font-bold hover:bg-gray-200 transition-colors disabled:opacity-50"
            >
              取消
            </button>
            <button 
              onClick={handleUpload}
              disabled={isUploading || items.length === 0 || !selectedDatasetId}
              className="px-8 py-2.5 bg-[#12B7F5] text-white rounded-full font-bold shadow-lg shadow-blue-100 hover:bg-[#0EA1D9] transition-all disabled:opacity-50 flex items-center space-x-2"
            >
              {isUploading ? <Loader2 size={18} className="animate-spin" /> : <Check size={18} />}
              <span>{isUploading ? '正在上传...' : '开始上传'}</span>
            </button>
          </div>
        </div>
      </motion.div>
    </div>
  );
};

export default UploadModal;
