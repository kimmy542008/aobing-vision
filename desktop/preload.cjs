const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('tv',{call:(action,data)=>ipcRenderer.invoke('tv',action,data)});
