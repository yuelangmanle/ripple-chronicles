
import React, { useState, useEffect } from 'react';
import { supabase } from '@/lib/supabase';
import { motion } from 'framer-motion';
import { 
  Image as ImageIcon, 
  Search, 
  Filter, 
  Plus, 
  Grid, 
  List, 
  Download, 
  Trash2,
  CheckCircle2,
} from 'lucide-react';

import UploadModal from '@/components/UploadModal';
import { exportToWord } from '@/lib/export';
import SpeciesAutocomplete from '@/components/SpeciesAutocomplete';

interface PlanktonImage {
  id: string;
  image_url: string;
  custom_name: string;
  created_at: string;
  dataset_id: string;
  species_id: string;
  species?: {
    name_cn: string;
    name_latin: string;
  };
}

const Gallery: React.FC = () => {
  const [images, setImages] = useState<PlanktonImage[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedImages, setSelectedImages] = useState<string[]>([]);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  useEffect(() => {
    fetchImages();
  }, []);

  async function fetchImages() {
    setLoading(true);
    const { data, error } = await supabase
      .from('plankton_images')
      .select('*, species(name_cn, name_latin)')
      .order('created_at', { ascending: false });

    if (error) {
      console.error('Error fetching images:', error);
    } else {
      setImages(data || []);
    }
    setLoading(false);
  }

  const handleRename = async (id: string, species: { id: string, name_cn: string }) => {
    const { error } = await supabase
      .from('plankton_images')
      .update({ species_id: species.id, custom_name: species.name_cn })
      .eq('id', id);

    if (error) {
      console.error('Error renaming image:', error);
    } else {
      setEditingId(null);
      fetchImages();
    }
  };

  const toggleSelect = (id: string) => {
    setSelectedImages(prev => 
      prev.includes(id) ? prev.filter(i => i !== id) : [...prev, id]
    );
  };

  const handleExport = async () => {
    const selected = images.filter(img => selectedImages.includes(img.id));
    if (selected.length === 0) return;

    const exportData = selected.map(img => ({
      url: img.image_url,
      name: img.species?.name_cn || img.custom_name
    }));

    await exportToWord(exportData);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">全库照片</h1>
          <p className="text-sm text-gray-500 mt-1">查看、筛选及管理所有已保存的浮游生物照片</p>
        </div>
        <div className="flex items-center space-x-3">
          <button 
            onClick={() => setShowUploadModal(true)}
            className="bg-[#12B7F5] text-white px-6 py-2.5 rounded-full font-bold shadow-lg shadow-blue-100 hover:bg-[#0EA1D9] transition-all flex items-center space-x-2"
          >
            <Plus size={20} />
            <span>上传照片</span>
          </button>
        </div>
      </div>

      {/* Toolbar */}
      <div className="flex items-center justify-between bg-white p-2 rounded-2xl shadow-sm border border-gray-100">
        <div className="flex items-center space-x-2 px-2">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -transform -translate-y-1/2 text-gray-400" size={18} />
            <input 
              type="text" 
              placeholder="搜索物种名称..." 
              className="pl-10 pr-4 py-2 bg-gray-50 border-none rounded-xl text-sm focus:ring-2 focus:ring-[#12B7F5] w-64 transition-all"
            />
          </div>
          <button className="p-2 text-gray-400 hover:text-[#12B7F5] hover:bg-blue-50 rounded-xl transition-all">
            <Filter size={20} />
          </button>
        </div>

        <div className="flex items-center space-x-2 pr-2">
          {selectedImages.length > 0 && (
            <motion.div 
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="flex items-center space-x-2 mr-4"
            >
              <span className="text-sm font-bold text-[#12B7F5] bg-blue-50 px-3 py-1.5 rounded-full">
                已选 {selectedImages.length} 项
              </span>
              <button 
                onClick={handleExport}
                className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-xl transition-all" 
                title="导出到 Word"
              >
                <Download size={20} />
              </button>
              <button className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-xl transition-all" title="删除">
                <Trash2 size={20} />
              </button>
              <div className="h-6 w-[1px] bg-gray-200 mx-2" />
            </motion.div>
          )}
          
          <div className="flex bg-gray-50 p-1 rounded-xl">
            <button 
              onClick={() => setViewMode('grid')}
              className={`p-2 rounded-lg transition-all ${viewMode === 'grid' ? 'bg-white text-[#12B7F5] shadow-sm' : 'text-gray-400'}`}
            >
              <Grid size={18} />
            </button>
            <button 
              onClick={() => setViewMode('list')}
              className={`p-2 rounded-lg transition-all ${viewMode === 'list' ? 'bg-white text-[#12B7F5] shadow-sm' : 'text-gray-400'}`}
            >
              <List size={18} />
            </button>
          </div>
        </div>
      </div>

      {/* Gallery Content */}
      {loading ? (
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          {[1, 2, 3, 4, 5, 6].map(i => (
            <div key={i} className="aspect-square bg-white rounded-2xl animate-pulse border border-gray-100" />
          ))}
        </div>
      ) : (
        <div className={viewMode === 'grid' 
          ? "grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4" 
          : "space-y-2"
        }>
          {images.map((img) => (
            <motion.div
              key={img.id}
              whileHover={{ y: -4 }}
              onClick={() => toggleSelect(img.id)}
              className={`relative overflow-hidden cursor-pointer group transition-all duration-300 ${
                viewMode === 'grid' 
                  ? "aspect-square rounded-2xl border-2 " + (selectedImages.includes(img.id) ? "border-[#12B7F5] shadow-lg shadow-blue-100" : "border-white shadow-sm")
                  : "bg-white p-3 rounded-xl border flex items-center space-x-4 " + (selectedImages.includes(img.id) ? "border-[#12B7F5] bg-blue-50/30" : "border-gray-100")
              }`}
            >
              <img 
                src={img.image_url} 
                alt={img.custom_name}
                className={viewMode === 'grid' ? "w-full h-full object-cover" : "w-12 h-12 rounded-lg object-cover"}
              />
              
              {viewMode === 'grid' ? (
                <>
                  <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity p-3 flex flex-col justify-end">
                    {editingId === img.id ? (
                      <div onClick={(e) => e.stopPropagation()}>
                        <SpeciesAutocomplete 
                          initialValue={img.species?.name_cn || img.custom_name}
                          onSelect={(s) => handleRename(img.id, s)}
                        />
                      </div>
                    ) : (
                      <div onDoubleClick={(e) => { e.stopPropagation(); setEditingId(img.id); }}>
                        <p className="text-white text-xs font-bold truncate">{img.species?.name_cn || img.custom_name}</p>
                        <p className="text-white/70 text-[10px] italic truncate">{img.species?.name_latin}</p>
                      </div>
                    )}
                  </div>
                  {selectedImages.includes(img.id) && (
                    <div className="absolute top-2 right-2 text-[#12B7F5] bg-white rounded-full shadow-md">
                      <CheckCircle2 size={20} fill="currentColor" className="text-white" />
                    </div>
                  )}
                </>
              ) : (
                <div className="flex-1 flex items-center justify-between">
                  <div>
                    <p className="font-bold text-gray-800">{img.species?.name_cn || img.custom_name}</p>
                    <p className="text-xs italic text-gray-500">{img.species?.name_latin}</p>
                  </div>
                  <p className="text-xs text-gray-400">{new Date(img.created_at).toLocaleDateString()}</p>
                </div>
              )}
            </motion.div>
          ))}
          {images.length === 0 && (
            <div className="col-span-full py-20 text-center bg-white rounded-3xl border-2 border-dashed border-gray-200">
              <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <ImageIcon className="text-gray-300" size={32} />
              </div>
              <p className="text-gray-500">照片库空空如也，快去上传一张吧</p>
            </div>
          )}
        </div>
      )}
      
      <UploadModal 
        isOpen={showUploadModal} 
        onClose={() => setShowUploadModal(false)} 
        onSuccess={fetchImages}
      />
    </div>
  );
};

export default Gallery;
