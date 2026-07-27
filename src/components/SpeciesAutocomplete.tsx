
import React, { useState, useEffect, useRef } from 'react';
import { supabase } from '@/lib/supabase';
import { Search, Loader2, X } from 'lucide-react';

interface Species {
  id: string;
  name_cn: string;
  name_latin: string;
}

interface Props {
  onSelect: (species: Species) => void;
  placeholder?: string;
  initialValue?: string;
}

const SpeciesAutocomplete: React.FC<Props> = ({ onSelect, placeholder = '搜索物种...', initialValue = '' }) => {
  const [query, setQuery] = useState(initialValue);
  const [results, setResults] = useState<Species[]>([]);
  const [loading, setLoading] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (query.length < 1) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      const { data, error } = await supabase
        .from('species')
        .select('id, name_cn, name_latin')
        .or(`name_cn.ilike.%${query}%,name_latin.ilike.%${query}%`)
        .limit(10);

      if (!error && data) {
        setResults(data);
        setShowDropdown(true);
      }
      setLoading(false);
    }, 300);

    return () => clearTimeout(timer);
  }, [query]);

  return (
    <div className="relative" ref={dropdownRef}>
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -transform -translate-y-1/2 text-gray-400" size={16} />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => query.length > 0 && setShowDropdown(true)}
          placeholder={placeholder}
          className="w-full pl-10 pr-4 py-2 bg-gray-50 border-none rounded-xl text-sm focus:ring-2 focus:ring-[#12B7F5] transition-all"
        />
        {loading && <Loader2 className="absolute right-3 top-1/2 -transform -translate-y-1/2 text-gray-400 animate-spin" size={16} />}
        {query && !loading && (
          <button 
            onClick={() => { setQuery(''); setResults([]); }}
            className="absolute right-3 top-1/2 -transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
          >
            <X size={14} />
          </button>
        )}
      </div>

      {showDropdown && results.length > 0 && (
        <div className="absolute z-50 w-full mt-2 bg-white rounded-2xl shadow-xl border border-gray-100 overflow-hidden">
          {results.map((s) => (
            <button
              key={s.id}
              onClick={() => {
                onSelect(s);
                setQuery(s.name_cn);
                setShowDropdown(false);
              }}
              className="w-full text-left px-4 py-3 hover:bg-blue-50 transition-colors flex flex-col"
            >
              <span className="text-sm font-bold text-gray-800">{s.name_cn}</span>
              <span className="text-xs italic text-gray-500">{s.name_latin}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default SpeciesAutocomplete;
