import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import AppEditor from './pages/AppEditor';
import AppRuntime from './pages/AppRuntime';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/app/:appId/editor" element={<AppEditor />} />
        <Route path="/app/:appId/runtime" element={<AppRuntime />} />
      </Routes>
    </Router>
  );
}

export default App;
