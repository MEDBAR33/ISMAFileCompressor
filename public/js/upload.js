// File upload and compression handling
let uploadedFiles = [];
let currentSessionId = null;

document.addEventListener('DOMContentLoaded', function() {
    initializeUpload();
    initializeButtons();
    setDefaultQuality();
});

function setDefaultQuality() {
    // Set Balanced as default
    const balancedCard = document.querySelector('.quality-card[data-quality="balanced"]');
    if (balancedCard) {
        balancedCard.click();
        window.selectedQuality = 'balanced';
    }
}

function initializeUpload() {
    const uploadZone = document.getElementById('uploadZone');
    const fileInput = document.getElementById('fileInput');
    const uploadButton = uploadZone?.querySelector('.btn-upload');
    
    if (!uploadZone || !fileInput) return;
    
    // Click to browse
    if (uploadButton) {
        uploadButton.addEventListener('click', () => fileInput.click());
    }
    uploadZone.addEventListener('click', (e) => {
        // Don't trigger if clicking on quality cards
        if (e.target.closest('.quality-card')) {
            return;
        }
        if (e.target === uploadZone || e.target.closest('.upload-content')) {
            fileInput.click();
        }
    });
    
    // File input change
    fileInput.addEventListener('change', handleFileSelect);
    
    // Drag and drop
    uploadZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadZone.classList.add('drag-over');
    });
    
    uploadZone.addEventListener('dragleave', () => {
        uploadZone.classList.remove('drag-over');
    });
    
    uploadZone.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadZone.classList.remove('drag-over');
        const files = Array.from(e.dataTransfer.files);
        handleFiles(files);
    });
}

function handleFileSelect(e) {
    const files = Array.from(e.target.files);
    handleFiles(files);
}

function handleFiles(files) {
    // Filter supported files
    const supportedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'tiff', 'tif', 'webp',
                                 'pdf', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx',
                                 'mp3', 'wav', 'flac', 'aac', 'ogg',
                                 'mp4', 'avi', 'mkv', 'mov', 'wmv',
                                 'zip', 'rar', '7z', 'tar', 'gz'];
    
    const validFiles = files.filter(file => {
        const ext = file.name.split('.').pop().toLowerCase();
        return supportedExtensions.includes(ext);
    });
    
    if (validFiles.length === 0) {
        alert('No supported files selected. Please select images, documents, videos, or archives.');
        return;
    }
    
    // Add files to list
    validFiles.forEach(file => {
        if (!uploadedFiles.find(f => f.name === file.name && f.size === file.size)) {
            uploadedFiles.push(file);
        }
    });
    
    updateFileList();
    updateStats();
}

function updateFileList() {
    const fileList = document.getElementById('fileList');
    if (!fileList) return;
    
    fileList.innerHTML = '';
    
    uploadedFiles.forEach((file, index) => {
        const fileItem = document.createElement('div');
        fileItem.className = 'file-item';
        fileItem.innerHTML = `
            <div class="file-icon">
                <i class="fas fa-file"></i>
            </div>
            <div class="file-info">
                <div class="file-name">${file.name}</div>
                <div class="file-size">${formatFileSize(file.size)}</div>
            </div>
            <button class="file-remove" onclick="removeFile(${index})">
                <i class="fas fa-times"></i>
            </button>
        `;
        fileList.appendChild(fileItem);
    });
}

function removeFile(index) {
    uploadedFiles.splice(index, 1);
    updateFileList();
    updateStats();
}

function updateStats() {
    const totalFiles = document.getElementById('totalFiles');
    const totalSize = document.getElementById('totalSize');
    const estimatedSave = document.getElementById('estimatedSave');
    
    if (totalFiles) totalFiles.textContent = uploadedFiles.length;
    
    const totalSizeBytes = uploadedFiles.reduce((sum, file) => sum + file.size, 0);
    if (totalSize) totalSize.textContent = formatFileSize(totalSizeBytes);
    
    // Estimate savings (rough calculation)
    const estimatedSavings = totalSizeBytes * 0.5; // Assume 50% savings
    if (estimatedSave) estimatedSave.textContent = formatFileSize(estimatedSavings);
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function initializeButtons() {
    const compressBtn = document.getElementById('compressBtn');
    const clearBtn = document.getElementById('clearBtn');
    
    if (compressBtn) {
        compressBtn.addEventListener('click', startCompression);
    }
    
    if (clearBtn) {
        clearBtn.addEventListener('click', clearFiles);
    }
}

function clearFiles() {
    uploadedFiles = [];
    updateFileList();
    updateStats();
    const fileInput = document.getElementById('fileInput');
    if (fileInput) fileInput.value = '';
}

async function startCompression() {
    if (uploadedFiles.length === 0) {
        alert('Please select files to compress first.');
        return;
    }
    
    try {
        // Show processing section
        const processingSection = document.getElementById('processingSection');
        const uploadSection = document.querySelector('#upload');
        
        if (processingSection) processingSection.style.display = 'block';
        if (uploadSection) uploadSection.scrollIntoView({ behavior: 'smooth' });
        
        // Upload files first
        const formData = new FormData();
        uploadedFiles.forEach(file => {
            formData.append('files', file);
        });
        
        // Upload files
        const uploadResponse = await fetch('/api/upload', {
            method: 'POST',
            body: formData
        });
        
        if (!uploadResponse.ok) {
            throw new Error('Upload failed');
        }
        
        const uploadData = await uploadResponse.json();
        currentSessionId = uploadData.sessionId;
        
        // Start compression
        const options = window.getCompressionOptions ? window.getCompressionOptions() : {
            compressionLevel: 'BALANCED',
            resizeImages: false,
            maxWidth: 1920,
            maxHeight: 1080
        };
        
        const compressResponse = await fetch('/api/compress', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: currentSessionId,
                level: options.compressionLevel.toLowerCase(),
                resize: options.resizeImages,
                maxWidth: options.maxWidth,
                maxHeight: options.maxHeight
            })
        });
        
        if (!compressResponse.ok) {
            let errorMessage = 'Compression start failed';
            try {
                const errorData = await compressResponse.json();
                errorMessage = errorData.error || errorMessage;
            } catch (e) {
                errorMessage = `Compression start failed (${compressResponse.status})`;
            }
            throw new Error(errorMessage);
        }
        
        const compressData = await compressResponse.json();
        console.log('Compression started:', compressData);
        
        // Initialize progress display
        const progressFill = document.getElementById('progressFill');
        const progressPercent = document.getElementById('progressPercent');
        const processedCount = document.getElementById('processedCount');
        const totalCount = document.getElementById('totalCount');
        
        if (progressFill) progressFill.style.width = '0%';
        if (progressPercent) progressPercent.textContent = '0%';
        if (processedCount) processedCount.textContent = '0';
        if (totalCount) totalCount.textContent = uploadedFiles.length;
        
        // Poll for progress
        pollCompressionProgress();
        
    } catch (error) {
        console.error('Compression error:', error);
        alert('Compression failed: ' + error.message);
        const processingSection = document.getElementById('processingSection');
        if (processingSection) processingSection.style.display = 'none';
    }
}

async function pollCompressionProgress() {
    if (!currentSessionId) return;
    
    const interval = setInterval(async () => {
        try {
            const response = await fetch(`/api/progress/${currentSessionId}`);
            if (!response.ok) {
                if (response.status === 404) {
                    // Session not found, stop polling
                    clearInterval(interval);
                    return;
                }
                return;
            }
            
            const status = await response.json();
            
            // Update progress
            const progressFill = document.getElementById('progressFill');
            const progressPercent = document.getElementById('progressPercent');
            const processedCount = document.getElementById('processedCount');
            const totalCount = document.getElementById('totalCount');
            const remainingTime = document.getElementById('remainingTime');
            
            if (progressFill) {
                const progress = status.progress || 0;
                progressFill.style.width = progress + '%';
            }
            if (progressPercent) {
                const progress = status.progress || 0;
                progressPercent.textContent = Math.round(progress) + '%';
            }
            if (processedCount) {
                processedCount.textContent = status.processedFiles || 0;
            }
            if (totalCount) {
                totalCount.textContent = status.totalFiles || uploadedFiles.length;
            }
            if (remainingTime) {
                if (status.formattedRemaining) {
                    remainingTime.textContent = status.formattedRemaining + ' remaining';
                } else if (status.estimatedTime) {
                    remainingTime.textContent = status.estimatedTime;
                } else {
                    remainingTime.textContent = 'Calculating...';
                }
            }
            
            // Check if completed
            if (status.status === 'completed') {
                clearInterval(interval);
                showResults(status);
            } else if (status.status === 'error') {
                clearInterval(interval);
                alert('Compression failed: ' + (status.error || 'Unknown error'));
                const processingSection = document.getElementById('processingSection');
                if (processingSection) processingSection.style.display = 'none';
            }
        } catch (error) {
            console.error('Progress poll error:', error);
        }
    }, 1000); // Poll every second
}

function showResults(status) {
    const processingSection = document.getElementById('processingSection');
    const resultsSection = document.getElementById('resultsSection');
    
    if (processingSection) processingSection.style.display = 'none';
    if (resultsSection) {
        resultsSection.style.display = 'block';
        resultsSection.scrollIntoView({ behavior: 'smooth' });
    }
    
    // Update result stats
    const successCount = document.getElementById('successCount');
    const totalSaved = document.getElementById('totalSaved');
    const compressionRatio = document.getElementById('compressionRatio');
    const totalTime = document.getElementById('totalTime');
    
    if (successCount) successCount.textContent = status.processedFiles || 0;
    // You would get these from the actual result
    if (totalSaved) totalSaved.textContent = '0 MB'; // Update from result
    if (compressionRatio) compressionRatio.textContent = '0%'; // Update from result
    if (totalTime) totalTime.textContent = '0s'; // Update from result
}

// Make functions available globally
window.removeFile = removeFile;
window.startCompression = startCompression;

