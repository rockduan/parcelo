sudo rm -rf /var/www/parcelo-frontend/frontend/
sudo cp -r frontend/dist/frontend/ /var/www/parcelo-frontend/
sudo systemctl reload nginx
