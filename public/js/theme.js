// Theme switching functionality
document.addEventListener('DOMContentLoaded', function() {
    const themeToggle = document.getElementById('themeToggle');
    
    if (themeToggle) {
        themeToggle.addEventListener('click', toggleTheme);
    }
    
    // Load saved theme
    const savedTheme = localStorage.getItem('theme') || 'dark';
    applyTheme(savedTheme);
});

function toggleTheme() {
    const currentTheme = document.documentElement.classList.contains('dark-theme') ? 'dark' : 'light';
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    applyTheme(newTheme);
    localStorage.setItem('theme', newTheme);
}

function applyTheme(theme) {
    const html = document.documentElement;
    const themeToggle = document.getElementById('themeToggle');
    
    if (theme === 'dark') {
        html.classList.remove('light-theme');
        html.classList.add('dark-theme');
        if (themeToggle) {
            themeToggle.querySelector('.fa-sun').style.display = 'none';
            themeToggle.querySelector('.fa-moon').style.display = 'block';
        }
    } else {
        html.classList.remove('dark-theme');
        html.classList.add('light-theme');
        if (themeToggle) {
            themeToggle.querySelector('.fa-sun').style.display = 'block';
            themeToggle.querySelector('.fa-moon').style.display = 'none';
        }
    }
}

