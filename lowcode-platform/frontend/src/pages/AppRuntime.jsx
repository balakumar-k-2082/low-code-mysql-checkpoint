import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Edit, ArrowLeft, Plus, X } from 'lucide-react';
import { getApp, getForms, getFields, getRecords, createRecord, updateRecord, deleteRecord } from '../api';
import '../styles/AppRuntime.css';

function AppRuntime() {
  const { appId } = useParams();
  const navigate = useNavigate();
  const [app, setApp] = useState(null);
  const [forms, setForms] = useState([]);
  const [selectedForm, setSelectedForm] = useState(null);
  const [fields, setFields] = useState([]);
  const [records, setRecords] = useState([]);
  const [showAddRecord, setShowAddRecord] = useState(false);
  const [formData, setFormData] = useState({});

  useEffect(() => {
    loadApp();
  }, [appId]);

  useEffect(() => {
    if (selectedForm) {
      loadFormData();
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

  const loadFormData = async () => {
    try {
      const [fieldsRes, recordsRes] = await Promise.all([
        getFields(appId, selectedForm.id),
        getRecords(appId, selectedForm.id)
      ]);
      setFields(fieldsRes.data);
      setRecords(recordsRes.data);
    } catch (error) {
      console.error('Failed to load form data:', error);
    }
  };

  const handleAddRecord = async (e) => {
    e.preventDefault();
    try {
      await createRecord(appId, selectedForm.id, { data: formData, createdBy: 'demo_user' });
      loadFormData();
      setFormData({});
      setShowAddRecord(false);
    } catch (error) {
      console.error('Failed to add record:', error);
    }
  };

  const handleDeleteRecord = async (recordId) => {
    if (window.confirm('Delete this record?')) {
      try {
        await deleteRecord(appId, recordId);
        loadFormData();
      } catch (error) {
        console.error('Failed to delete record:', error);
      }
    }
  };

  if (!app) return <div className="loading">Loading...</div>;

  return (
    <div className="app-runtime">
      <header className="runtime-header">
        <div className="header-left">
          <button onClick={() => navigate('/')} className="btn-icon"><ArrowLeft /></button>
          <h1>{app.name}</h1>
        </div>
        <div className="header-right">
          <button onClick={() => navigate(`/app/${appId}/editor`)} className="btn-secondary">
            <Edit size={18} /> Edit App
          </button>
        </div>
      </header>

      <div className="runtime-content">
        <aside className="sidebar">
          <h3>Forms</h3>
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
                <h2>{selectedForm.name} Report</h2>
                <button onClick={() => setShowAddRecord(true)} className="btn-primary">
                  <Plus size={18} /> Add Record
                </button>
              </div>

              <div className="report-table">
                <table>
                  <thead>
                    <tr>
                      {fields.map(field => <th key={field.id}>{field.label}</th>)}
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.map(record => (
                      <tr key={record.id}>
                        {fields.map(field => (
                          <td key={field.id}>{record.data[field.name] || '-'}</td>
                        ))}
                        <td>
                          <button onClick={() => handleDeleteRecord(record.id)} className="btn-danger-small">Delete</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {records.length === 0 && <div className="empty">No records yet. Add your first record!</div>}
              </div>
            </>
          ) : (
            <div className="empty">Select a form to view records</div>
          )}
        </main>
      </div>

      {showAddRecord && selectedForm && (
        <div className="modal-overlay" onClick={() => setShowAddRecord(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Add {selectedForm.name}</h2>
              <button onClick={() => setShowAddRecord(false)} className="btn-icon"><X /></button>
            </div>
            <form onSubmit={handleAddRecord}>
              {fields.map(field => (
                <div key={field.id} className="form-group">
                  <label>{field.label} {field.isRequired && <span className="required">*</span>}</label>
                  {field.fieldType === 'textarea' ? (
                    <textarea
                      value={formData[field.name] || ''}
                      onChange={e => setFormData({...formData, [field.name]: e.target.value})}
                      required={field.isRequired}
                    />
                  ) : field.fieldType === 'checkbox' ? (
                    <input
                      type="checkbox"
                      checked={formData[field.name] || false}
                      onChange={e => setFormData({...formData, [field.name]: e.target.checked})}
                    />
                  ) : (
                    <input
                      type={field.fieldType}
                      value={formData[field.name] || ''}
                      onChange={e => setFormData({...formData, [field.name]: e.target.value})}
                      required={field.isRequired}
                    />
                  )}
                </div>
              ))}
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowAddRecord(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Add Record</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default AppRuntime;
