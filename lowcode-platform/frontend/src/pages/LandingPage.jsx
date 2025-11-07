import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Folder } from 'lucide-react';
import { getApps, createApp } from '../api';
import '../styles/LandingPage.css';

function LandingPage() {
  const [apps, setApps] = useState([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newAppName, setNewAppName] = useState('');
  const [newAppDesc, setNewAppDesc] = useState('');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    loadApps();
  }, []);

  const loadApps = async () => {
    try {
      const response = await getApps();
      setApps(response.data);
    } catch (error) {
      console.error('Failed to load apps:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateApp = async (e) => {
    e.preventDefault();
    try {
      const newApp = {
        name: newAppName,
        description: newAppDesc,
        createdBy: 'demo_user'
      };
      const response = await createApp(newApp);
      setApps([response.data, ...apps]);
      setShowCreateModal(false);
      setNewAppName('');
      setNewAppDesc('');
      // Navigate to editor
      navigate(`/app/${response.data.id}/editor`);
    } catch (error) {
      console.error('Failed to create app:', error);
      alert('Failed to create app');
    }
  };

  const openEditor = (appId) => {
    navigate(`/app/${appId}/editor`);
  };

  return (
    <div className="landing-page">
      <header className="landing-header">
        <h1>Low-Code Platform</h1>
        <p>Build applications visually with transaction-based undo/redo</p>
      </header>

      <div className="landing-content">
        <div className="apps-header">
          <h2>My Applications</h2>
          <button className="btn-primary" onClick={() => setShowCreateModal(true)}>
            <Plus size={20} />
            Create App
          </button>
        </div>

        {loading ? (
          <div className="loading">Loading apps...</div>
        ) : apps.length === 0 ? (
          <div className="empty-state">
            <Folder size={64} />
            <h3>No applications yet</h3>
            <p>Create your first app to get started</p>
            <button className="btn-primary" onClick={() => setShowCreateModal(true)}>
              <Plus size={20} />
              Create Your First App
            </button>
          </div>
        ) : (
          <div className="apps-grid">
            {apps.map((app) => (
              <div key={app.id} className="app-card" onClick={() => openEditor(app.id)}>
                <div className="app-icon">
                  <Folder size={32} />
                </div>
                <h3>{app.name}</h3>
                <p>{app.description || 'No description'}</p>
                <div className="app-meta">
                  <span>Created {new Date(app.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2>Create New App</h2>
            <form onSubmit={handleCreateApp}>
              <div className="form-group">
                <label>App Name *</label>
                <input
                  type="text"
                  value={newAppName}
                  onChange={(e) => setNewAppName(e.target.value)}
                  placeholder="e.g., CRM System"
                  required
                  autoFocus
                />
              </div>
              <div className="form-group">
                <label>Description</label>
                <textarea
                  value={newAppDesc}
                  onChange={(e) => setNewAppDesc(e.target.value)}
                  placeholder="Brief description of your app"
                  rows={3}
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn-primary">
                  Create App
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default LandingPage;
