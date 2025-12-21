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
    
    if (!uploadZone || !fileInput) {
        console.error('Upload zone or file input not found');
        return;
    }
    
    console.log('Initializing upload zone:', uploadZone, 'File input:', fileInput);
    
    // Click to browse - button click (use direct selector, not nested)
    const browseButton = document.querySelector('.btn-upload') || uploadZone.querySelector('.btn-upload');
    if (browseButton) {
        console.log('Browse button found:', browseButton);
        
        // Remove any existing event listeners by cloning
        const newButton = browseButton.cloneNode(true);
        browseButton.parentNode.replaceChild(newButton, browseButton);
        
        // Add click handler to the new button
        newButton.onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log('Browse button clicked - opening file dialog');
            if (fileInput) {
                fileInput.click();
                console.log('File input clicked');
            }
            return false;
        };
        
    } else {
        console.warn('Browse button (.btn-upload) not found');
    }
    
    // Click to browse - upload zone click
    uploadZone.addEventListener('click', (e) => {
        // Don't trigger if clicking on quality cards
        if (e.target.closest('.quality-card')) {
            return;
        }
        // Don't trigger if clicking the button (already handled)
        if (e.target.closest('.btn-upload')) {
            return;
        }
        // Don't trigger if clicking on buttons or interactive elements
        if (e.target.tagName === 'BUTTON' || e.target.closest('button')) {
            return;
        }
        
        console.log('Upload zone clicked');
        e.stopPropagation(); // Stop propagation but don't prevent default
        fileInput.click();
    });
    
    // File input change
    fileInput.addEventListener('change', (e) => {
        console.log('File input changed, files:', e.target.files);
        handleFileSelect(e);
    });
    
    // Update stats when quality mode changes (even after files are uploaded)
    document.addEventListener('qualityChanged', function() {
        console.log('Quality changed, updating stats');
        updateStats();
    });
    
    // Also listen for custom quality slider changes
    const qualitySlider = document.getElementById('qualitySlider');
    if (qualitySlider) {
        qualitySlider.addEventListener('input', function() {
            if (window.selectedQuality === 'custom') {
                updateStats();
            }
        });
    }
    
    // Drag and drop - dragover
    uploadZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        e.stopPropagation();
        uploadZone.classList.add('drag-over');
        return false;
    });
    
    // Drag and drop - dragenter
    uploadZone.addEventListener('dragenter', (e) => {
        e.preventDefault();
        e.stopPropagation();
        uploadZone.classList.add('drag-over');
        return false;
    });
    
    // Drag and drop - dragleave
    uploadZone.addEventListener('dragleave', (e) => {
        e.preventDefault();
        e.stopPropagation();
        // Only remove class if we're actually leaving the zone
        if (!uploadZone.contains(e.relatedTarget)) {
            uploadZone.classList.remove('drag-over');
        }
        return false;
    });
    
    // Drag and drop - drop
    uploadZone.addEventListener('drop', (e) => {
        e.preventDefault();
        e.stopPropagation();
        uploadZone.classList.remove('drag-over');
        
        const files = Array.from(e.dataTransfer.files);
        console.log('Files dropped:', files.length);
        if (files.length > 0) {
            handleFiles(files);
        }
        return false;
    });
    
    // Also handle drag events on document to prevent default browser behavior
    // But only prevent default outside the upload zone
    document.addEventListener('dragover', (e) => {
        // Only prevent if NOT in upload zone
        if (!e.target.closest('#uploadZone')) {
            e.preventDefault();
        }
        return false;
    });
    
    document.addEventListener('drop', (e) => {
        // Only prevent if NOT in upload zone
        if (!e.target.closest('#uploadZone')) {
            e.preventDefault();
        }
        return false;
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
    
    // Calculate estimated savings based on selected quality mode
    const quality = window.selectedQuality || 'balanced';
    let compressionRatio = 0.5; // Default 50%
    
    switch(quality) {
        case 'maximum':
            // Maximum: 65-80% savings (use conservative 60% to account for files that don't compress well)
            compressionRatio = 0.60;
            break;
        case 'balanced':
            // Balanced: 40-60% savings (use conservative 35% to account for files that don't compress well)
            compressionRatio = 0.35;
            break;
        case 'best':
            // Best Quality: 20-35% savings (use conservative 20% to account for files that don't compress well)
            compressionRatio = 0.20;
            break;
        case 'custom':
            // Custom: depends on quality slider (20-80% range)
            const qualitySlider = document.getElementById('qualitySlider');
            if (qualitySlider) {
                const qualityValue = parseInt(qualitySlider.value) || 75;
                // Lower quality = higher compression (inverse relationship)
                // Quality 30 = 80% compression, Quality 90 = 20% compression
                compressionRatio = 1.0 - ((qualityValue - 20) / 70.0 * 0.6); // Range: 0.2 to 0.8
                compressionRatio = Math.max(0.2, Math.min(0.8, compressionRatio));
            } else {
                compressionRatio = 0.5;
            }
            break;
    }
    
    const estimatedSavings = totalSizeBytes * compressionRatio;
    if (estimatedSave) {
        estimatedSave.textContent = formatFileSize(estimatedSavings);
        
        // Make sure label is "Estimated Save" (not "Total Saved") when files are uploaded but not compressed yet
        const estimatedSaveLabel = estimatedSave.parentElement?.parentElement?.querySelector('.stat-label');
        if (estimatedSaveLabel && !estimatedSaveLabel.textContent.includes('Total')) {
            estimatedSaveLabel.textContent = 'Estimated Save';
        }
    }
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
    const cancelBtn = document.getElementById('cancelBtn');
    const newCompressionBtn = document.getElementById('newCompressionBtn');
    const viewFilesBtn = document.getElementById('viewFilesBtn');
    
    if (compressBtn) {
        compressBtn.addEventListener('click', startCompression);
    }
    
    if (clearBtn) {
        clearBtn.addEventListener('click', clearFiles);
    }
    
    if (cancelBtn) {
        cancelBtn.addEventListener('click', cancelCompression);
    }
    
    // New Compression button - opens file picker
    if (newCompressionBtn) {
        newCompressionBtn.addEventListener('click', function() {
            const fileInput = document.getElementById('fileInput');
            if (fileInput) {
                // Clear previous files
                fileInput.value = '';
                // Reset UI
                resetUI();
                // Open file picker
                fileInput.click();
            }
        });
    }
    
    // View Files button - opens file explorer with highlighted files
    if (viewFilesBtn) {
        viewFilesBtn.addEventListener('click', function() {
            const outputDirPath = document.getElementById('outputDirectoryPath');
            if (outputDirPath && outputDirPath.textContent) {
                const outputPath = outputDirPath.textContent.trim();
                openFileExplorer(outputPath);
            } else {
                // Fallback: get from result or settings
                fetch('/api/settings')
                    .then(res => res.json())
                    .then(settings => {
                        if (settings.outputFolder) {
                            openFileExplorer(settings.outputFolder);
                        } else {
                            alert('Output directory not found');
                        }
                    })
                    .catch(err => {
                        console.error('Failed to get output folder:', err);
                        alert('Could not open file explorer. Output directory not available.');
                    });
            }
        });
    }
}

// Function to open file explorer and highlight files (Windows)
function openFileExplorer(directoryPath) {
    // Normalize path for Windows
    const normalizedPath = directoryPath.replace(/\//g, '\\');
    
    // Try to use Windows explorer command
    // Note: In a web browser, we can't directly execute system commands,
    // so we'll use a file:// URL which should open the folder
    // For highlighting specific files, we'd need a backend API
    
    // Open directory in file explorer via API
    fetch('/api/open-folder', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ path: normalizedPath })
    })
    .then(response => response.json())
    .then(data => {
        if (!data.success) {
            // Fallback: show alert with path
            alert('Output directory: ' + normalizedPath + '\n\nPlease navigate to this folder manually in File Explorer.');
        }
    })
    .catch(err => {
        console.error('Failed to open folder:', err);
        // Fallback: show alert with path
        alert('Output directory: ' + normalizedPath + '\n\nPlease navigate to this folder manually in File Explorer.');
    });
}

function resetUI() {
    uploadedFiles = [];
    currentSessionId = null;
    updateFileList();
    
    // Reset processing section
    const processingSection = document.getElementById('processingSection');
    if (processingSection) {
        processingSection.style.display = 'none';
    }
    
    // Reset results section
    const resultsSection = document.getElementById('resultsSection');
    if (resultsSection) {
        resultsSection.style.display = 'none';
    }
    
    // Reset progress bar
    resetProgressBar();
    
    // Scroll to upload section
    const uploadSection = document.getElementById('upload');
    if (uploadSection) {
        uploadSection.scrollIntoView({ behavior: 'smooth' });
    }
}

function resetProgressBar() {
    const progressFill = document.getElementById('progressFill');
    const progressPercent = document.getElementById('progressPercent');
    const processedCount = document.getElementById('processedCount');
    const totalCount = document.getElementById('totalCount');
    const remainingTime = document.getElementById('remainingTime');
    
    if (progressFill) {
        progressFill.style.width = '0%';
        progressFill.style.background = 'linear-gradient(90deg, #27d27d, #2ecc71, #27ae60)';
    }
    if (progressPercent) {
        progressPercent.textContent = '0%';
    }
    if (processedCount) {
        processedCount.textContent = '0';
    }
    if (totalCount) {
        totalCount.textContent = '0';
    }
    if (remainingTime) {
        remainingTime.textContent = 'Compressing...';
    }
}

function clearFiles() {
    uploadedFiles = [];
    updateFileList();
    updateStats();
    const fileInput = document.getElementById('fileInput');
    if (fileInput) fileInput.value = '';
    
    // Reset Estimated Save label back to "Estimated Save"
    const estimatedSaveLabel = document.querySelector('#estimatedSave')?.parentElement?.parentElement?.querySelector('.stat-label');
    if (estimatedSaveLabel) {
        estimatedSaveLabel.textContent = 'Estimated Save';
    }
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
        
        if (processingSection) {
            processingSection.style.display = 'block';
            processingSection.style.visibility = 'visible';
            console.log('Processing section displayed');
            // Scroll to processing section (progress bar)
            setTimeout(() => {
                processingSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }, 100);
        } else {
            console.error('Processing section element not found!');
        }
        
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
        const remainingTime = document.getElementById('remainingTime');
        
        if (progressFill) {
            progressFill.style.width = '0%';
            // Set green gradient for active compression
            progressFill.style.background = 'linear-gradient(90deg, #27d27d, #2ecc71, #27ae60)';
        }
        if (progressPercent) progressPercent.textContent = '0%';
        if (processedCount) processedCount.textContent = '0';
        if (totalCount) totalCount.textContent = uploadedFiles.length;
        if (remainingTime) {
            remainingTime.textContent = 'Compressing...';
        }
        
        // Poll for progress
        pollCompressionProgress();
        
    } catch (error) {
        console.error('Compression error:', error);
        alert('Compression failed: ' + error.message);
        const processingSection = document.getElementById('processingSection');
        if (processingSection) processingSection.style.display = 'none';
    }
}

let progressInterval = null;

async function pollCompressionProgress() {
    if (!currentSessionId) return;
    
    // Clear any existing interval
    if (progressInterval) {
        clearInterval(progressInterval);
    }
    
    progressInterval = setInterval(async () => {
        try {
            const response = await fetch(`/api/progress/${currentSessionId}`);
            if (!response.ok) {
                if (response.status === 404) {
                    // Session not found, stop polling
                    console.log('Session not found (404), stopping polling');
                    if (progressInterval) {
                        clearInterval(progressInterval);
                        progressInterval = null;
                    }
                    return;
                }
                console.error('Progress API error:', response.status, response.statusText);
                return;
            }
            
            // Check if response has content
            const text = await response.text();
            if (!text || text.trim().length === 0) {
                console.warn('Empty response from progress API, skipping');
                return;
            }
            
            let status;
            try {
                status = JSON.parse(text);
            } catch (parseError) {
                console.error('Failed to parse JSON response:', parseError);
                console.error('Response text:', text.substring(0, 200));
                return;
            }
            
            // Debug logging - log full status for debugging
            console.log('=== Progress update received ===', {
                status: status.status,
                progress: status.progress,
                totalFiles: status.totalFiles,
                processedFiles: status.processedFiles,
                hasResult: !!status.result,
                resultKeys: status.result ? Object.keys(status.result) : null,
                error: status.error,
                fullStatus: JSON.stringify(status).substring(0, 500) // First 500 chars
            });
            
            // Update progress
            const progressFill = document.getElementById('progressFill');
            const progressPercent = document.getElementById('progressPercent');
            const processedCount = document.getElementById('processedCount');
            const totalCount = document.getElementById('totalCount');
            const remainingTime = document.getElementById('remainingTime');
            
            // Calculate progress - use backend progress or calculate from file counts
            let progress = parseInt(status.progress) || 0;
            const totalFilesCount = parseInt(status.totalFiles) || 0;
            const processedFilesCount = parseInt(status.processedFiles) || 0;
            
            // Check status first (needed for progress check) - be very permissive
            const statusLower = (status.status || '').toLowerCase().trim();
            const isCompleted = statusLower === 'completed' || 
                               statusLower === 'complete' ||
                               statusLower.includes('complete') ||
                               statusLower === 'done' ||
                               statusLower === 'finished';
            const hasResult = status.result !== undefined && status.result !== null && Object.keys(status.result).length > 0;
            
            // If completed or has result, force to 100%
            if (isCompleted || hasResult) {
                progress = 100;
            } else if (totalFilesCount > 0 && processedFilesCount >= 0) {
                // Calculate progress from file counts
                const calculatedProgress = Math.round((processedFilesCount / totalFilesCount) * 100);
                // Use the higher of backend progress or calculated progress
                progress = Math.max(progress, calculatedProgress);
                // Cap at 100%
                progress = Math.min(100, progress);
            }
            
            // Force progress to update even if it's 0 (show that compression is happening)
            if (progress === 0 && !isCompleted && statusLower !== 'error' && statusLower !== 'cancelled') {
                // Show minimal progress to indicate activity (but only if we haven't started processing)
                if (totalFilesCount > 0 && processedFilesCount === 0) {
                    progress = 1; // Show 1% to indicate activity
                }
            }
            
            // Calculate final progress value (must be declared before use)
            const finalProgress = Math.max(0, Math.min(100, progress));
            
            console.log('Progress update:', { 
                backendProgress: status.progress, 
                calculated: totalFilesCount > 0 ? Math.round((processedFilesCount / totalFilesCount) * 100) : 0,
                final: progress,
                finalProgress: finalProgress,
                processedFiles: processedFilesCount,
                totalFiles: totalFilesCount,
                status: status.status,
                statusLower: statusLower,
                isCompleted: isCompleted,
                hasResult: hasResult,
                result: status.result
            });
            
            // Update progress bar with smooth animation
            const widthValue = finalProgress + '%';
            
            // Force update with explicit values and error checking
            if (progressFill) {
                progressFill.style.width = widthValue;
                // Use solid green when complete, gradient during progress
                if (finalProgress >= 100 || isCompleted || hasResult) {
                    progressFill.style.background = '#27ae60'; // Solid green when complete (like Microsoft Store)
                } else {
                    progressFill.style.background = 'linear-gradient(90deg, #27d27d, #2ecc71, #27ae60)';
                }
                progressFill.style.display = 'block';
                progressFill.style.transition = 'width 0.3s ease-out, background 0.2s ease-out'; // Smooth transition
                console.log('Progress bar updated to:', widthValue, 'Status:', statusLower, 'Final Progress:', finalProgress);
            } else {
                console.error('progressFill element not found!');
            }
            
            if (progressPercent) {
                progressPercent.textContent = finalProgress + '%';
                console.log('Progress percent updated to:', finalProgress + '%');
            } else {
                console.error('progressPercent element not found!');
            }
            
            // Update file counts - ensure they're accurate (ALWAYS update based on actual counts)
            if (processedCount) {
                // Use processedFilesCount from backend, but if completed/hasResult and it's still 0, use totalFilesCount
                let displayProcessed = processedFilesCount;
                if ((isCompleted || hasResult) && processedFilesCount === 0 && totalFilesCount > 0) {
                    displayProcessed = totalFilesCount;
                }
                processedCount.textContent = displayProcessed || 0;
                console.log('Updated processedCount to:', displayProcessed, 'from processedFilesCount:', processedFilesCount);
            } else {
                console.error('processedCount element not found!');
            }
            
            if (totalCount) {
                totalCount.textContent = totalFilesCount || uploadedFiles.length || 0;
            } else {
                console.error('totalCount element not found!');
            }
            if (remainingTime) {
                if (isCompleted || hasResult || finalProgress >= 100) {
                    remainingTime.textContent = 'Complete!';
                } else {
                    // Always show "Compressing..." during compression
                    remainingTime.textContent = 'Compressing...';
                }
            }
            
            // Check if completed, error, or cancelled (stop polling for any terminal state)
            const progressValue = parseInt(status.progress) || 0;
            
            // Check if result exists (indicates completion) - already defined above, use it
            const allFilesProcessed = totalFilesCount > 0 && processedFilesCount >= totalFilesCount;
            const progressComplete = finalProgress >= 100;
            
            console.log('Status check:', {
                status: statusLower,
                statusRaw: status.status,
                progress: progressValue,
                calculatedProgress: progress,
                totalFiles: totalFilesCount,
                processedFiles: processedFilesCount,
                hasResult: hasResult,
                isCompleted: isCompleted,
                allFilesProcessed: allFilesProcessed,
                progressComplete: progressComplete,
                error: status.error
            });
            
            // Stop polling if: status is terminal OR progress is 100% AND we've processed all files
            // Also check if result exists (indicates completion)
            // BE VERY AGGRESSIVE about stopping - if all files processed, stop regardless
            
            // BE VERY AGGRESSIVE about stopping - if all files processed, stop regardless
            const shouldStop = isCompleted || 
                             hasResult ||
                             statusLower === 'error' || 
                             statusLower === 'cancelled' ||
                             (allFilesProcessed && totalFilesCount > 0 && processedFilesCount >= totalFilesCount) ||
                             (progressComplete && allFilesProcessed && totalFilesCount > 0) ||
                             (totalFilesCount > 0 && processedFilesCount >= totalFilesCount && finalProgress >= 100);
            
            console.log('Completion check:', {
                shouldStop: shouldStop,
                isCompleted: isCompleted,
                hasResult: hasResult,
                allFilesProcessed: allFilesProcessed,
                progressComplete: progressComplete,
                totalFiles: totalFilesCount,
                processedFiles: processedFilesCount
            });
            
            if (shouldStop) {
                console.log('*** STOPPING POLLING *** - Status:', statusLower, 'Progress:', progressValue, 'HasResult:', hasResult, 'IsCompleted:', isCompleted);
                
                // Handle completion - check multiple conditions
                const isReallyComplete = isCompleted || hasResult || (allFilesProcessed && totalFilesCount > 0);
                
                if (isReallyComplete) {
                    console.log('*** COMPLETION HANDLER TRIGGERED ***', {
                        isCompleted,
                        hasResult,
                        allFilesProcessed,
                        totalFiles: totalFilesCount,
                        processedFiles: processedFilesCount
                    });
                    
                    // Force completion values
                    const finalProgressValue = 100;
                    const finalProcessedCount = totalFilesCount || 1;
                    
                    // Ensure progress shows 100% with solid green
                    if (progressFill) {
                        progressFill.style.width = '100%';
                        // Use solid green when complete (like Microsoft Store)
                        progressFill.style.background = '#27ae60';
                        progressFill.style.transition = 'width 0.3s ease-out, background 0.2s ease-out';
                        console.log('✓ Progress bar set to 100% with solid green');
                    }
                    if (progressPercent) {
                        progressPercent.textContent = '100%';
                        console.log('✓ Progress percent set to 100%');
                    }
                    if (processedCount) {
                        processedCount.textContent = finalProcessedCount;
                        console.log('✓ Processed count set to:', finalProcessedCount);
                    }
                    if (remainingTime) {
                        remainingTime.textContent = 'Complete!';
                        console.log('✓ Remaining time set to Complete!');
                    }
                    console.log('Compression completed - stopping polling and showing results:', status);
                    // Update estimated save to total saved immediately
                    updateEstimatedSaveToTotalSaved(status);
                    
                    // STOP POLLING HERE
                    if (progressInterval) {
                        clearInterval(progressInterval);
                        progressInterval = null;
                        console.log('✓ Polling stopped');
                    }
                    
                    // Small delay to ensure UI updates are visible
                    setTimeout(() => {
                        showResults(status);
                    }, 500);
                } else if (statusLower === 'error' || status.error) {
                    // Show error message
                    const errorMsg = status.error || 'Unknown error occurred during compression';
                    alert('Compression failed: ' + errorMsg);
                    const processingSection = document.getElementById('processingSection');
                    if (processingSection) processingSection.style.display = 'none';
                    // Stop polling
                    if (progressInterval) {
                        clearInterval(progressInterval);
                        progressInterval = null;
                        console.log('✓ Polling stopped (error)');
                    }
                } else if (statusLower === 'cancelled') {
                    // Already handled by cancelCompression function
                    console.log('Compression cancelled');
                    // Stop polling
                    if (progressInterval) {
                        clearInterval(progressInterval);
                        progressInterval = null;
                        console.log('✓ Polling stopped (cancelled)');
                    }
                } else if (progressValue >= 100 && processedFilesCount >= totalFilesCount && totalFilesCount > 0) {
                    // Progress is 100% and all files processed, but status might not be set
                    // Check if there are errors
                    if (status.error) {
                        alert('Compression failed: ' + status.error);
                        const processingSection = document.getElementById('processingSection');
                        if (processingSection) processingSection.style.display = 'none';
                    } else {
                        // Assume completed
                        if (progressFill) {
                            progressFill.style.width = '100%';
                            // Use solid green when complete
                            progressFill.style.background = '#27ae60';
                            progressFill.style.transition = 'width 0.3s ease-out, background 0.2s ease-out';
                        }
                        if (progressPercent) {
                            progressPercent.textContent = '100%';
                        }
                        if (processedCount && totalCount) {
                            processedCount.textContent = totalCount.textContent;
                        }
                        if (remainingTime) {
                            remainingTime.textContent = 'Complete!';
                        }
                        // Update estimated save immediately
                        updateEstimatedSaveToTotalSaved(status);
                        setTimeout(() => {
                            showResults(status);
                            // Automatically download all compressed files to user's device
                            if (status.result && status.result.downloadFiles) {
                                const sessionId = status.sessionId || currentSessionId;
                                if (sessionId) {
                                    autoDownloadFiles(status.result.downloadFiles, sessionId);
                                }
                            }
                        }, 500);
                    }
                }
            }
        } catch (error) {
            console.error('Progress poll error:', error);
        }
    }, 1000); // Poll every second
}

// Helper function to update Estimated Save to Total Saved immediately when completion is detected
function updateEstimatedSaveToTotalSaved(status) {
    if (status.result) {
        const result = status.result;
        const estimatedSaveValue = document.getElementById('estimatedSave');
        if (estimatedSaveValue) {
            const estimatedSaveLabel = estimatedSaveValue.parentElement?.parentElement?.querySelector('.stat-label');
            
            if (result.totalSaved !== undefined) {
                const savedBytes = result.totalSaved || 0;
                const compressionRatio = result.compressionRatio || result.overallCompressionRatio || 0;
                // Show saved bytes with percentage
                estimatedSaveValue.textContent = formatFileSize(savedBytes) + ' (' + compressionRatio.toFixed(1) + '%)';
                console.log('Updated Estimated Save to Total Saved:', savedBytes, 'bytes,', compressionRatio.toFixed(1) + '%');
            }
            if (estimatedSaveLabel) {
                estimatedSaveLabel.textContent = 'Total Saved';
                console.log('Updated label to Total Saved');
            }
        }
    }
}

function showResults(status) {
    console.log('showResults called with status:', status);
    const processingSection = document.getElementById('processingSection');
    const resultsSection = document.getElementById('resultsSection');
    
    if (processingSection) processingSection.style.display = 'none';
    if (resultsSection) {
        resultsSection.style.display = 'block';
        resultsSection.scrollIntoView({ behavior: 'smooth' });
    }
    
    // Update result stats - handle new inline format with icons
    const successCountEl = document.getElementById('successCount');
    if (successCountEl) {
        // Update the span inside the result-value
        successCountEl.textContent = status.processedFiles || 0;
    }
    
    const totalSavedEl = document.getElementById('totalSaved');
    const compressionRatioEl = document.getElementById('compressionRatio');
    const totalTimeEl = document.getElementById('totalTime');
    
    // Update from result if available
    if (status.result) {
        const result = status.result;
        if (totalSavedEl) {
            const savedBytes = result.totalSaved || 0;
            totalSavedEl.textContent = formatFileSize(savedBytes);
        }
        if (compressionRatioEl) {
            const ratio = result.overallCompressionRatio || result.compressionRatio || 0;
            compressionRatioEl.textContent = ratio.toFixed(1) + '%';
        }
        if (totalTimeEl) {
            const timeMs = result.timeMs || 0;
            if (timeMs < 1000) {
                totalTimeEl.textContent = timeMs + 'ms';
            } else if (timeMs < 60000) {
                totalTimeEl.textContent = (timeMs / 1000).toFixed(1) + 's';
            } else {
                const minutes = Math.floor(timeMs / 60000);
                const seconds = Math.floor((timeMs % 60000) / 1000);
                totalTimeEl.textContent = minutes + 'm ' + seconds + 's';
            }
        }
        
        // Update Estimated Save to Total Save with exact bytes and percentage
        const estimatedSaveValue = document.getElementById('estimatedSave');
        const estimatedSaveLabel = estimatedSaveValue?.parentElement?.parentElement?.querySelector('.stat-label');
        if (estimatedSaveValue && result.totalSaved !== undefined) {
            const savedBytes = result.totalSaved || 0;
            const compressionRatio = result.compressionRatio || result.overallCompressionRatio || 0;
            // Show saved bytes with percentage
            estimatedSaveValue.textContent = formatFileSize(savedBytes) + ' (' + compressionRatio.toFixed(1) + '%)';
            console.log('Updated Estimated Save to Total Saved:', savedBytes, 'bytes,', compressionRatio.toFixed(1) + '%');
        }
        if (estimatedSaveLabel) {
            estimatedSaveLabel.textContent = 'Total Saved';
        }
        
        // Display output directory if available
        if (result.outputDirectory) {
            const outputDirElement = document.getElementById('outputDirectory');
            const outputDirPath = document.getElementById('outputDirectoryPath');
            if (outputDirElement) {
                outputDirElement.style.display = 'block';
            }
            if (outputDirPath) {
                outputDirPath.textContent = result.outputDirectory;
                console.log('Output directory displayed:', result.outputDirectory);
            }
        } else {
            // Fallback: try to get from settings API or use default
            fetch('/api/settings')
                .then(res => res.json())
                .then(settings => {
                    if (settings.outputFolder) {
                        const outputDirElement = document.getElementById('outputDirectory');
                        const outputDirPath = document.getElementById('outputDirectoryPath');
                        if (outputDirElement) {
                            outputDirElement.style.display = 'block';
                        }
                        if (outputDirPath) {
                            outputDirPath.textContent = settings.outputFolder;
                        }
                    }
                })
                .catch(err => console.error('Failed to get output folder:', err));
        }
    } else {
        // Fallback if no result
        if (totalSavedEl) totalSavedEl.textContent = '0 MB';
        if (compressionRatioEl) compressionRatioEl.textContent = '0%';
        if (totalTimeEl) totalTimeEl.textContent = '0s';
    }
}

// Automatically download compressed files to user's device (Downloads folder)
function autoDownloadFiles(downloadFiles, sessionId) {
    if (!downloadFiles || downloadFiles.length === 0) {
        console.log('No files to download');
        return;
    }
    
    console.log('Auto-downloading', downloadFiles.length, 'compressed file(s) to Downloads folder...');
    
    // Download each file with a small delay to avoid browser blocking multiple downloads
    downloadFiles.forEach((file, index) => {
        setTimeout(() => {
            try {
                const downloadUrl = file.downloadUrl || `/api/download/${encodeURIComponent(file.compressedName)}?sessionId=${sessionId}`;
                const link = document.createElement('a');
                link.href = downloadUrl;
                link.download = file.originalName || file.compressedName;
                link.style.display = 'none';
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                console.log('Downloaded:', file.originalName || file.compressedName);
            } catch (error) {
                console.error('Error downloading file:', file.originalName, error);
            }
        }, index * 300); // 300ms delay between each download
    });
    
    // Show notification
    if (downloadFiles.length > 0) {
        showNotification(`Downloading ${downloadFiles.length} file(s) to your Downloads folder...`, 'info');
    }
}

// Helper function to show notifications
function showNotification(message, type = 'info') {
    // Create notification element
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: ${type === 'info' ? '#2196F3' : type === 'success' ? '#4CAF50' : '#f44336'};
        color: white;
        padding: 16px 24px;
        border-radius: 8px;
        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        z-index: 10000;
        font-size: 14px;
        max-width: 400px;
        animation: slideIn 0.3s ease-out;
    `;
    notification.textContent = message;
    document.body.appendChild(notification);
    
    // Remove after 5 seconds
    setTimeout(() => {
        notification.style.animation = 'slideOut 0.3s ease-out';
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
        }, 300);
    }, 5000);
}

async function cancelCompression() {
    if (!currentSessionId) return;
    
    try {
        const response = await fetch(`/api/cancel/${currentSessionId}`, {
            method: 'POST'
        });
        
        if (response.ok) {
            const data = await response.json();
            console.log('Compression cancelled:', data);
            
            // Stop polling
            if (progressInterval) {
                clearInterval(progressInterval);
                progressInterval = null;
            }
            
            // Hide processing section
            const processingSection = document.getElementById('processingSection');
            if (processingSection) {
                processingSection.style.display = 'none';
            }
            
            alert('Compression has been cancelled.');
        } else {
            console.error('Failed to cancel compression');
        }
    } catch (error) {
        console.error('Error cancelling compression:', error);
    }
}

// Make functions available globally
window.removeFile = removeFile;
window.startCompression = startCompression;
window.cancelCompression = cancelCompression;

