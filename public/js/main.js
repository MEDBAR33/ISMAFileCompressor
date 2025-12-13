// Main application logic
document.addEventListener('DOMContentLoaded', function() {
    console.log('FileCompressor Pro initialized');
    
    // Initialize components
    initializeQualitySelection();
    initializeNavigation();
    initializeCustomSettings();
    
    // Check if we're on the upload section
    if (window.location.hash === '#upload' || !window.location.hash) {
        // Already initialized by upload.js
    }
});

function initializeQualitySelection() {
    const qualityCards = document.querySelectorAll('.quality-card');
    
    qualityCards.forEach(card => {
        card.addEventListener('click', function(e) {
            // Stop event propagation to prevent conflicts
            e.stopPropagation();
            
            // Don't flip if clicking on input elements inside custom card
            if (this.dataset.quality === 'custom' && this.classList.contains('flipped')) {
                if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'LABEL') {
                    return; // Allow interaction with form elements
                }
            }
            
            // Remove active class from all cards
            qualityCards.forEach(c => {
                c.classList.remove('active');
                const badge = c.querySelector('.selected-badge');
                if (badge) badge.remove();
            });
            
            // Add active class to clicked card
            this.classList.add('active');
            
            // Add selected badge (only on front face)
            const front = this.querySelector('.flip-card-front') || this;
            if (!front.querySelector('.selected-badge')) {
                const badge = document.createElement('div');
                badge.className = 'selected-badge';
                badge.innerHTML = '<i class="fas fa-check"></i>';
                front.appendChild(badge);
            }
            
            // Flip custom card if selected
            if (this.dataset.quality === 'custom') {
                this.classList.add('flipped');
            } else {
                // Remove flip from custom card if another is selected
                const customCard = document.querySelector('.quality-card[data-quality="custom"]');
                if (customCard) {
                    customCard.classList.remove('flipped');
                    // Remove badge from custom card back
                    const customBack = customCard.querySelector('.flip-card-back');
                    if (customBack) {
                        const badge = customBack.querySelector('.selected-badge');
                        if (badge) badge.remove();
                    }
                }
            }
            
            // Update selected quality
            window.selectedQuality = this.dataset.quality;
            console.log('Selected quality:', window.selectedQuality);
        });
    });
}

function initializeNavigation() {
    const navLinks = document.querySelectorAll('.nav-link');
    
    navLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            // Remove active class from all links
            navLinks.forEach(l => l.classList.remove('active'));
            
            // Add active class to clicked link
            this.classList.add('active');
            
            // Scroll to section
            const targetId = this.getAttribute('href');
            if (targetId && targetId !== '#') {
                const target = document.querySelector(targetId);
                if (target) {
                    target.scrollIntoView({ behavior: 'smooth' });
                }
            }
        });
    });
}

function initializeCustomSettings() {
    const qualitySlider = document.getElementById('qualitySlider');
    const qualityValue = document.getElementById('qualityValue');
    
    if (qualitySlider && qualityValue) {
        qualitySlider.addEventListener('input', function() {
            qualityValue.textContent = this.value + '%';
        });
    }
    
    const resizeToggle = document.getElementById('resizeToggle');
    const resizeSettings = document.getElementById('resizeSettings');
    
    if (resizeToggle && resizeSettings) {
        resizeToggle.addEventListener('change', function() {
            resizeSettings.style.display = this.checked ? 'block' : 'none';
        });
    }
}

// Utility function to get selected compression options
function getCompressionOptions() {
    const quality = window.selectedQuality || 'balanced';
    
    const options = {
        compressionLevel: quality.toUpperCase(),
        outputDirectory: null, // Will use default
        resizeImages: false,
        maxWidth: 1920,
        maxHeight: 1080,
        convertPngToJpeg: false,
        outputFormat: 'auto'
    };
    
    // Get custom settings if custom is selected
    if (quality === 'custom') {
        const qualitySlider = document.getElementById('qualitySlider');
        const formatSelect = document.getElementById('formatSelect');
        const resizeToggle = document.getElementById('resizeToggle');
        const maxWidth = document.getElementById('maxWidth');
        const maxHeight = document.getElementById('maxHeight');
        
        if (qualitySlider) {
            options.quality = parseInt(qualitySlider.value);
        }
        if (formatSelect) {
            options.outputFormat = formatSelect.value;
        }
        if (resizeToggle && resizeToggle.checked) {
            options.resizeImages = true;
            if (maxWidth) options.maxWidth = parseInt(maxWidth.value) || 1920;
            if (maxHeight) options.maxHeight = parseInt(maxHeight.value) || 1080;
        }
    } else {
        // Set defaults based on quality
        switch(quality) {
            case 'maximum':
                options.resizeImages = true;
                options.maxWidth = 1280;
                options.maxHeight = 720;
                options.convertPngToJpeg = true;
                break;
            case 'balanced':
                options.resizeImages = false;
                options.maxWidth = 1920;
                options.maxHeight = 1080;
                options.convertPngToJpeg = true;
                break;
            case 'best':
                options.resizeImages = false;
                options.maxWidth = 3840;
                options.maxHeight = 2160;
                options.convertPngToJpeg = false;
                break;
        }
    }
    
    return options;
}

// API helper functions
async function apiCall(endpoint, method = 'GET', data = null) {
    try {
        const options = {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            }
        };
        
        if (data && method !== 'GET') {
            options.body = JSON.stringify(data);
        }
        
        const response = await fetch(`/api${endpoint}`, options);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        return await response.json();
    } catch (error) {
        console.error('API call failed:', error);
        throw error;
    }
}

// Export for use in other files
window.getCompressionOptions = getCompressionOptions;
window.apiCall = apiCall;

