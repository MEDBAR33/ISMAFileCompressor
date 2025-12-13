// Animation and visual effects
document.addEventListener('DOMContentLoaded', function() {
    initializeAnimations();
    initializeScrollAnimations();
});

function initializeAnimations() {
    // Add fade-in animation to cards
    const cards = document.querySelectorAll('.quality-card, .feature-card');
    cards.forEach((card, index) => {
        card.style.opacity = '0';
        card.style.transform = 'translateY(20px)';
        card.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
        
        setTimeout(() => {
            card.style.opacity = '1';
            card.style.transform = 'translateY(0)';
        }, index * 100);
    });
}

function initializeScrollAnimations() {
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };
    
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('animate-in');
            }
        });
    }, observerOptions);
    
    // Observe elements that should animate on scroll
    document.querySelectorAll('.feature-card, .quality-card').forEach(el => {
        observer.observe(el);
    });
}

// Add CSS for animations if not already present
const style = document.createElement('style');
style.textContent = `
    .animate-in {
        animation: fadeInUp 0.6s ease forwards;
    }
    
    @keyframes fadeInUp {
        from {
            opacity: 0;
            transform: translateY(30px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
    
    .drag-over {
        border-color: #4a90e2 !important;
        background-color: rgba(74, 144, 226, 0.1) !important;
    }
    
    .file-item {
        display: flex;
        align-items: center;
        padding: 12px;
        background: rgba(255, 255, 255, 0.05);
        border-radius: 8px;
        margin-bottom: 8px;
        transition: background 0.2s;
    }
    
    .file-item:hover {
        background: rgba(255, 255, 255, 0.1);
    }
    
    .file-icon {
        margin-right: 12px;
        font-size: 24px;
        color: #4a90e2;
    }
    
    .file-info {
        flex: 1;
    }
    
    .file-name {
        font-weight: 500;
        color: #fff;
        margin-bottom: 4px;
    }
    
    .file-size {
        font-size: 12px;
        color: #aaa;
    }
    
    .file-remove {
        background: transparent;
        border: none;
        color: #ff4444;
        cursor: pointer;
        padding: 8px;
        border-radius: 4px;
        transition: background 0.2s;
    }
    
    .file-remove:hover {
        background: rgba(255, 68, 68, 0.2);
    }
`;
document.head.appendChild(style);

