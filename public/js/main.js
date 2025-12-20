// Main application logic
document.addEventListener('DOMContentLoaded', function() {
    console.log('FileCompressor Pro initialized');
    
    // Initialize components
    initializeQualitySelection();
    initializeNavigation();
    initializeCustomSettings();
    initializeAnimatedHeader();
    initializeFeatureCards();
    initializeMobileMenu();
    
    // Check if we're on the upload section
    if (window.location.hash === '#upload' || !window.location.hash) {
        // Already initialized by upload.js
    }
});

function initializeMobileMenu() {
    const mobileMenuToggle = document.getElementById('mobileMenuToggle');
    const navMenu = document.getElementById('navMenu');
    
    if (mobileMenuToggle && navMenu) {
        mobileMenuToggle.addEventListener('click', function() {
            navMenu.classList.toggle('active');
            mobileMenuToggle.classList.toggle('active');
        });
        
        // Close menu when clicking on a nav link
        const navLinks = navMenu.querySelectorAll('.nav-link');
        navLinks.forEach(link => {
            link.addEventListener('click', function() {
                navMenu.classList.remove('active');
                mobileMenuToggle.classList.remove('active');
            });
        });
        
        // Close menu when clicking outside
        document.addEventListener('click', function(e) {
            if (!navMenu.contains(e.target) && !mobileMenuToggle.contains(e.target)) {
                navMenu.classList.remove('active');
                mobileMenuToggle.classList.remove('active');
            }
        });
    }
}

function initializeFeatureCards() {
    const featureCards = document.querySelectorAll('.flip-feature-card');
    
    featureCards.forEach(card => {
        card.addEventListener('click', function() {
            // Toggle flipped class
            this.classList.toggle('flipped');
        });
    });
}

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
            
            // Trigger event to update estimated savings
            const event = new CustomEvent('qualityChanged', { detail: { quality: this.dataset.quality } });
            document.dispatchEvent(event);
            
            // Also directly update stats if files are already uploaded
            if (typeof updateStats === 'function') {
                updateStats();
            }
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
                // Special handling for Contact Us - scroll to contact section inside formats
                if (targetId === '#formats' && this.textContent.includes('Contact')) {
                    setTimeout(() => {
                        const contactSection = document.querySelector('#contact');
                        if (contactSection) {
                            contactSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        }
                    }, 100);
                } else {
                    const target = document.querySelector(targetId);
                    if (target) {
                        target.scrollIntoView({ behavior: 'smooth' });
                    }
                }
            }
        });
    });
    
    // Handle Developers button click
    const developersBtn = document.getElementById('developersBtn');
    if (developersBtn) {
        developersBtn.addEventListener('click', function(e) {
            e.preventDefault();
            const developersSection = document.querySelector('#developers');
            if (developersSection) {
                developersSection.scrollIntoView({ behavior: 'smooth' });
            }
        });
    }
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

function initializeAnimatedHeader() {
    const words = ['Incredible', 'Smart', 'Magnificent', 'Adorable'];
    const wordCycle = document.getElementById('wordCycle');
    const brandIsma = document.getElementById('brandIsma');
    const brandCompressor = document.getElementById('brandCompressor');
    
    if (!wordCycle || !brandIsma || !brandCompressor) return;
    
    let currentWordIndex = 0;
    
    // Initial state - show first word centered
    wordCycle.textContent = words[0];
    wordCycle.style.transform = 'translateX(-50%) rotateX(0deg)';
    wordCycle.style.opacity = '1';
    
    // Hide ISMA and FileCompressor initially
    brandIsma.style.transform = 'rotateY(90deg)';
    brandIsma.style.opacity = '0';
    brandCompressor.style.opacity = '0';
    brandCompressor.style.transform = 'translateX(-20px)';
    
    let step = 0;
    
    function animate() {
        step++;
        
        if (step <= 4) {
            // Phase 1: Cycle through words (4 words)
            if (step < 4) {
                // Flip out current word
                wordCycle.style.transform = 'translateX(-50%) rotateX(90deg)';
                wordCycle.style.opacity = '0';
                
                setTimeout(() => {
                    // Change to next word
                    currentWordIndex = (currentWordIndex + 1) % words.length;
                    wordCycle.textContent = words[currentWordIndex];
                    
                    // Flip in new word (centered)
                    wordCycle.style.transform = 'translateX(-50%) rotateX(0deg)';
                    wordCycle.style.opacity = '1';
                }, 600);
            } else {
                // After 4th word, hide and show ISMA
                wordCycle.style.transform = 'translateX(-50%) rotateX(90deg)';
                wordCycle.style.opacity = '0';
                
                setTimeout(() => {
                    brandIsma.style.transform = 'rotateY(0deg)';
                    brandIsma.style.opacity = '1';
                    
                    // Show FileCompressor after ISMA appears
                    setTimeout(() => {
                        brandCompressor.style.opacity = '1';
                        brandCompressor.style.transform = 'translateX(0)';
                    }, 800);
                }, 600);
            }
        } else if (step === 6) {
            // After showing ISMA FileCompressor, reset
            setTimeout(() => {
                // Reset everything
                currentWordIndex = 0;
                step = 0;
                wordCycle.textContent = words[0];
                wordCycle.style.transform = 'translateX(-50%) rotateX(0deg)';
                wordCycle.style.opacity = '1';
                brandIsma.style.transform = 'rotateY(90deg)';
                brandIsma.style.opacity = '0';
                brandCompressor.style.opacity = '0';
                brandCompressor.style.transform = 'translateX(-20px)';
            }, 2500); // Show ISMA FileCompressor for 2.5 seconds
        }
    }
    
    // Start animation cycle - each step is 2.5 seconds
    setInterval(animate, 2500);
}

// Export for use in other files
window.getCompressionOptions = getCompressionOptions;
window.apiCall = apiCall;

