
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";
import Home from "./pages/Home";
import Datasets from "./pages/Datasets";
import Gallery from "./pages/Gallery";
import Taxonomy from "./pages/Taxonomy";
import Settings from "./pages/Settings";

export default function App() {
  return (
    <Router>
      <Layout>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/datasets" element={<Datasets />} />
          <Route path="/gallery" element={<Gallery />} />
          <Route path="/taxonomy" element={<Taxonomy />} />
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </Layout>
    </Router>
  );
}
