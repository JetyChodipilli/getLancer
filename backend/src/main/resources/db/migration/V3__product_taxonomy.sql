CREATE TABLE product_categories(product_id uuid NOT NULL REFERENCES products,category_slug varchar(100) NOT NULL REFERENCES categories,PRIMARY KEY(product_id,category_slug));
CREATE TABLE product_technologies(product_id uuid NOT NULL REFERENCES products,technology_slug varchar(100) NOT NULL REFERENCES technologies,PRIMARY KEY(product_id,technology_slug));
INSERT INTO product_categories SELECT p.id,c.slug FROM products p JOIN categories c ON c.name=p.category;
INSERT INTO product_technologies SELECT p.id,t.slug FROM products p JOIN technologies t ON t.name=ANY(string_to_array(p.technology,','));
CREATE INDEX taxonomy_category_products ON product_categories(category_slug,product_id);
CREATE INDEX taxonomy_technology_products ON product_technologies(technology_slug,product_id);
