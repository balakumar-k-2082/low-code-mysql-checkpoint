import axios from 'axios';

const API_BASE_URL = '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Apps
export const getApps = () => api.get('/apps');
export const getApp = (appId) => api.get(`/apps/${appId}`);
export const createApp = (app) => api.post('/apps', app);
export const updateApp = (appId, app) => api.put(`/apps/${appId}`, app);
export const deleteApp = (appId) => api.delete(`/apps/${appId}`);

// Forms
export const getForms = (appId) => api.get(`/apps/${appId}/forms`);
export const getForm = (appId, formId) => api.get(`/apps/${appId}/forms/${formId}`);
export const createForm = (appId, form) => api.post(`/apps/${appId}/forms`, form);
export const updateForm = (appId, formId, form) => api.put(`/apps/${appId}/forms/${formId}`, form);
export const deleteForm = (appId, formId) => api.delete(`/apps/${appId}/forms/${formId}`);

// Fields
export const getFields = (appId, formId) => api.get(`/apps/${appId}/forms/${formId}/fields`);
export const createField = (appId, formId, field) => api.post(`/apps/${appId}/forms/${formId}/fields`, field);
export const updateField = (appId, fieldId, field) => api.put(`/apps/${appId}/fields/${fieldId}`, field);
export const deleteField = (appId, fieldId) => api.delete(`/apps/${appId}/fields/${fieldId}`);

// Reports
export const getReports = (appId, formId) => api.get(`/apps/${appId}/forms/${formId}/reports`);
export const updateReport = (appId, reportId, report) => api.put(`/apps/${appId}/reports/${reportId}`, report);

// Records
export const getRecords = (appId, formId, reportId = null) => {
  const url = `/apps/${appId}/forms/${formId}/records${reportId ? `?reportId=${reportId}` : ''}`;
  return api.get(url);
};
export const getRecord = (appId, recordId) => api.get(`/apps/${appId}/records/${recordId}`);
export const createRecord = (appId, formId, record) => api.post(`/apps/${appId}/forms/${formId}/records`, record);
export const updateRecord = (appId, recordId, record) => api.put(`/apps/${appId}/records/${recordId}`, record);
export const deleteRecord = (appId, recordId) => api.delete(`/apps/${appId}/records/${recordId}`);

// Checkpoints
export const setActiveUser = (appId, userId) => api.post(`/apps/${appId}/checkpoints/user`, { userId });
export const undo = (appId, userId) => api.post(`/apps/${appId}/checkpoints/undo?userId=${userId}`);
export const redo = (appId, userId) => api.post(`/apps/${appId}/checkpoints/redo?userId=${userId}`);
export const getActivities = (appId, userId) => api.get(`/apps/${appId}/checkpoints/activities?userId=${userId}`);
export const revertToCheckpoint = (appId, checkpointId, userId) =>
  api.post(`/apps/${appId}/checkpoints/revert/${checkpointId}?userId=${userId}`);

export default api;
