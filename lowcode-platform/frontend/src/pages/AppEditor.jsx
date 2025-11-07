import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Plus, Undo, Redo, Activity, Play, ArrowLeft } from 'lucide-react';
import { getApp, getForms, createForm, getFields, createField, undo, redo, getActivities, setActiveUser, revertToCheckpoint } from '../api';
import '../styles/AppEditor.css';

const CURRENT_USER = 'demo_user';

function AppEditor() {
  const { appId } = useParams();
  const navigate = useNavigate();
  const [app, setApp] = useState(null);
  const [forms, setForms] = useState([]);
  const [selectedForm, setSelectedForm] = useState(null);
  const [fields, setFields] = useState([]);
  const [showActivities, setShowActivities] = useState(false);
  const [activities, setActivities] = useState([]);
  const [showNewForm, setShowNewForm] = useState(false);
  const [showNewField, setShowNewField] = useState(false);
  const [newFormName, setNewFormName] = useState('');
  const [newFieldData, setNewFieldData] = useState({ name: '', label: '', fieldType: 'text', isRequired: false });

  useEffect(() => {
    loadApp();
    initCheckpoint();
  }, [appId]);

  useEffect(() => {
    if (selectedForm) {
      loadFields();
    }
  }, [selectedForm]);

  const loadApp = async () => {
    try {
      const [appRes, formsRes] = await Promise.all([getApp(appId), getForms(appId)]);
      setApp(appRes.data);
      setForms(formsRes.data);
      if (formsRes.data.length > 0) {
        setSelectedForm(formsRes.data[0]);
      }
    } catch (error) {
      console.error('Failed to load app:', error);
    }
  };

  const initCheckpoint = async () => {
    try {
      await setActiveUser(appId, CURRENT_USER);
    } catch (error) {
      console.error('Failed to init checkpoint:', error);
    }
  };

  const loadFields = async () => {
    try {
      const res = await getFields(appId, selectedForm.id);
      setFields(res.data);
    } catch (error) {
      console.error('Failed to load fields:', error);
    }
  };

  const handleCreateForm = async (e) => {
    e.preventDefault();
    try {
      const res = await createForm(appId, { name: newFormName });
      setForms([...forms, res.data]);
      setSelectedForm(res.data);
      setNewFormName('');
      setShowNewForm(false);
    } catch (error) {
      console.error('Failed to create form:', error);
    }
  };

  const handleCreateField = async (e) => {
    e.preventDefault();
    try {
      await createField(appId, selectedForm.id, newFieldData);
      loadFields();
      setNewFieldData({ name: '', label: '', fieldType: 'text', isRequired: false });
      setShowNewField(false);
    } catch (error) {
      console.error('Failed to create field:', error);
    }
  };

  const handleUndo = async () => {
    try {
      await undo(appId, CURRENT_USER);
      loadFields();
    } catch (error) {
      alert('Cannot undo: ' + error.response?.data?.message || error.message);
    }
  };

  const handleRedo = async () => {
    try {
      await redo(appId, CURRENT_USER);
      loadFields();
    } catch (error) {
      alert('Cannot redo: ' + error.response?.data?.message || error.message);
    }
  };

  const loadActivities = async () => {
    try {
      const res = await getActivities(appId, CURRENT_USER);
      setActivities(res.data);
      setShowActivities(true);
    } catch (error) {
      console.error('Failed to load activities:', error);
    }
  };

  const handleRevert = async (checkpointId) => {
    try {
      await revertToCheckpoint(appId, checkpointId, CURRENT_USER);
      loadFields();
      setShowActivities(false);
    } catch (error) {
      alert('Failed to revert: ' + error.message);
    }
  };

  if (!app) return <div className="loading">Loading...</div>;

  return (
    <div className="app-editor">
      <header className="editor-header">
        <div className="header-left">
          <button onClick={() => navigate('/')} className="btn-icon"><ArrowLeft /></button>
          <h1>{app.name}</h1>
        </div>
        <div className="header-right">
          <button onClick={handleUndo} className="btn-icon" title="Undo"><Undo /></button>
          <button onClick={handleRedo} className="btn-icon" title="Redo"><Redo /></button>
          <button onClick={loadActivities} className="btn-icon" title="Activities"><Activity /></button>
          <button onClick={() => navigate(`/app/${appId}/runtime`)} className="btn-primary">
            <Play size={18} /> Open App
          </button>
        </div>
      </header>

      <div className="editor-content">
        <aside className="sidebar">
          <div className="sidebar-header">
            <h3>Forms</h3>
            <button onClick={() => setShowNewForm(true)} className="btn-icon"><Plus size={18} /></button>
          </div>
          <div className="forms-list">
            {forms.map(form => (
              <div key={form.id} className={`form-item ${selectedForm?.id === form.id ? 'active' : ''}`} onClick={() => setSelectedForm(form)}>
                {form.name}
              </div>
            ))}
          </div>
        </aside>

        <main className="main-content">
          {selectedForm ? (
            <>
              <div className="content-header">
                <h2>{selectedForm.name}</h2>
                <button onClick={() => setShowNewField(true)} className="btn-primary"><Plus size={18} /> Add Field</button>
              </div>
              <div className="fields-list">
                {fields.map(field => (
                  <div key={field.id} className="field-card">
                    <strong>{field.label}</strong>
                    <span className="field-type">{field.fieldType}</span>
                    {field.isRequired && <span className="required-badge">Required</span>}
                  </div>
                ))}
                {fields.length === 0 && <div className="empty">No fields yet. Add your first field!</div>}
              </div>
            </>
          ) : (
            <div className="empty">Select a form or create one to get started</div>
          )}
        </main>
      </div>

      {showNewForm && (
        <div className="modal-overlay" onClick={() => setShowNewForm(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>Create Form</h2>
            <form onSubmit={handleCreateForm}>
              <div className="form-group">
                <label>Form Name *</label>
                <input value={newFormName} onChange={e => setNewFormName(e.target.value)} required autoFocus />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowNewForm(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Create</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showNewField && (
        <div className="modal-overlay" onClick={() => setShowNewField(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>Add Field</h2>
            <form onSubmit={handleCreateField}>
              <div className="form-group">
                <label>Field Name *</label>
                <input value={newFieldData.name} onChange={e => setNewFieldData({...newFieldData, name: e.target.value})} required />
              </div>
              <div className="form-group">
                <label>Label *</label>
                <input value={newFieldData.label} onChange={e => setNewFieldData({...newFieldData, label: e.target.value})} required />
              </div>
              <div className="form-group">
                <label>Field Type *</label>
                <select value={newFieldData.fieldType} onChange={e => setNewFieldData({...newFieldData, fieldType: e.target.value})}>
                  <option value="text">Text</option>
                  <option value="email">Email</option>
                  <option value="phone">Phone</option>
                  <option value="textarea">Textarea</option>
                  <option value="checkbox">Checkbox</option>
                  <option value="dropdown">Dropdown</option>
                  <option value="number">Number</option>
                  <option value="date">Date</option>
                </select>
              </div>
              <div className="form-group">
                <label><input type="checkbox" checked={newFieldData.isRequired} onChange={e => setNewFieldData({...newFieldData, isRequired: e.target.checked})} /> Required</label>
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowNewField(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Add Field</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showActivities && (
        <div className="activities-panel">
          <div className="panel-header">
            <h3>Activities</h3>
            <button onClick={() => setShowActivities(false)}>×</button>
          </div>
          <div className="activities-list">
            {activities.map(activity => (
              <div key={activity.id} className={`activity-item ${activity.isCurrent ? 'current' : ''}`} onClick={() => handleRevert(activity.id)}>
                <strong>{activity.name}</strong>
                <div className="activity-changes">
                  {activity.changes.map((change, i) => (
                    <div key={i}>{change.description}</div>
                  ))}
                </div>
                <small>{new Date(activity.createdAt).toLocaleString()}</small>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default AppEditor;
